package io.github.erbayaskin.eimza.api.validation;

import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.net.http.HttpClient;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.naming.ldap.LdapName;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import io.github.erbayaskin.eimza.api.error.ApiException;
import io.github.erbayaskin.eimza.api.security.ApiLimits;
import io.github.erbayaskin.eimza.api.security.BoundedBase64Decoder;
import io.github.erbayaskin.eimza.api.truststore.DatabaseTrustedCertificateProvider;
import io.github.erbayaskin.eimza.api.validation.policy.ValidationPolicyConfigurationResponse;
import io.github.erbayaskin.eimza.api.validation.policy.ValidationPolicyConfigurationService;
import io.github.erbayaskin.eimza.api.validation.policy.ValidationPolicyMode;
import io.github.erbayaskin.eimza.cades.CadesException;
import io.github.erbayaskin.eimza.cades.CadesSignatureInspector;
import io.github.erbayaskin.eimza.cades.CadesSignatureVerifier;
import io.github.erbayaskin.eimza.cades.SignaturePolicy;
import io.github.erbayaskin.eimza.core.model.ValidationIndication;
import io.github.erbayaskin.eimza.pades.PadesException;
import io.github.erbayaskin.eimza.pades.PadesSignatureVerifier;
import io.github.erbayaskin.eimza.validation.CertificateValidationPolicy;
import io.github.erbayaskin.eimza.validation.CertificateValidationRequest;
import io.github.erbayaskin.eimza.validation.CertificateValidator;
import io.github.erbayaskin.eimza.validation.DefaultCertificateValidator;
import io.github.erbayaskin.eimza.validation.EmbeddedFirstRevocationDataProvider;
import io.github.erbayaskin.eimza.validation.EmbeddedRevocationValue;
import io.github.erbayaskin.eimza.validation.NetworkRevocationDataProvider;
import io.github.erbayaskin.eimza.validation.RevocationDataProvider;
import io.github.erbayaskin.eimza.validation.TrustedCertificateProvider;
import io.github.erbayaskin.eimza.validation.ValidationCheck;
import io.github.erbayaskin.eimza.xades.XadesException;
import io.github.erbayaskin.eimza.xades.XadesSignatureVerifier;

@Service
public class ValidationService {

    private final CertificateValidator certificateValidator;
    private final DatabaseTrustedCertificateProvider trustedCertificates;
    private final ValidationProperties properties;
    private final ValidationPolicyConfigurationService policyConfigurationService;
    private final Clock clock;
    private final RevocationDataProvider revocationFallback;
    private final CadesSignatureInspector inspector = new CadesSignatureInspector();
    private final CadesSignatureVerifier signatureVerifier = new CadesSignatureVerifier();
    private final XadesSignatureVerifier xadesVerifier = new XadesSignatureVerifier();
    private final PadesSignatureVerifier padesVerifier = new PadesSignatureVerifier();

    public ValidationService(
            CertificateValidator certificateValidator,
            DatabaseTrustedCertificateProvider trustedCertificates,
            ValidationProperties properties,
            ValidationPolicyConfigurationService policyConfigurationService,
            Clock clock,
            RevocationDataProvider revocationFallback) {
        this.certificateValidator = certificateValidator;
        this.trustedCertificates = trustedCertificates;
        this.properties = properties;
        this.policyConfigurationService = policyConfigurationService;
        this.clock = clock;
        this.revocationFallback = revocationFallback;
    }

    public ValidationReport validateCertificate(CertificateValidationApiRequest request) {
        var validationTime = validationTime(request.validationTime());
        var policyConfiguration = policyConfigurationService.current();
        var certificatePolicy = effectiveCertificatePolicy(policyConfiguration);
        var certificate = decodeCertificate(request.certificate(), "certificate");
        var intermediates = request.intermediateCertificates().stream()
                .map(item -> decodeCertificate(item, "intermediateCertificates"))
                .toList();
        var result = certificateValidator.validate(new CertificateValidationRequest(
                certificate,
                intermediates,
                validationTime,
                effectivePolicyVersion(policyConfiguration),
                certificatePolicy));
        var checks = new ArrayList<>(result.checks());
        checks.addAll(passivePolicyChecks(policyConfiguration));
        var main = finalIndication(
                policyConfiguration,
                combine(result.cryptographicValidity(), result.turkishQualification()));
        return new ValidationReport(
                UUID.randomUUID(),
                "CERTIFICATE",
                main,
                result.cryptographicValidity(),
                result.turkishQualification(),
                validationTime,
                result.policyVersion(),
                policyConfiguration.mode(),
                passivePolicies(policyConfiguration),
                result.trustStoreVersion(),
                null,
                null,
                null,
                result.certificationPath(),
                checks,
                summary(main, result.cryptographicValidity(), result.turkishQualification()));
    }

    public ValidationReport validateSignature(SignatureValidationApiRequest request) {
        var validationTime = validationTime(request.validationTime());
        var policyConfiguration = policyConfigurationService.current();
        var certificatePolicy = effectiveCertificatePolicy(policyConfiguration);
        var signature = BoundedBase64Decoder.decode(
                request.signature(),
                "signature",
                "PADES".equals(request.format())
                        ? ApiLimits.MAX_DOCUMENT_BYTES
                        : ApiLimits.MAX_SIGNATURE_BYTES);
        var checks = new ArrayList<ValidationCheck>();
        var detectedSigner = trySignerInfo(request.format(), signature);
        try {
            java.util.Set<TrustAnchor> tsaRoots;
            try {
                var snapshot = trustedCertificates.snapshotAt(validationTime);
                tsaRoots = snapshot.certificates().stream()
                        .filter(item -> item.type() == TrustedCertificateProvider.TrustedCertificateType.ROOT)
                        .map(item -> new TrustAnchor(item.certificate(), null))
                        .collect(java.util.stream.Collectors.toSet());
            } catch (IllegalStateException noTrustStore) {
                tsaRoots = java.util.Set.of();
            }
            var inspection = inspectSignature(
                    request,
                    signature,
                    configuredSignaturePolicy(policyConfiguration),
                    tsaRoots,
                    policyConfiguration.signingCertificateValidityActive());
            checks.add(new ValidationCheck(
                    "SIGNATURE_CRYPTO_VALID",
                    ValidationIndication.VALID,
                    inspection.format() + " imzası ve imzalı içerik bağı geçerli.",
                    null,
                    inspection.details()));
            if (inspection.longTermMaterialVerified()) {
                checks.add(new ValidationCheck(
                        "LONG_TERM_VALIDATION_MATERIAL",
                        ValidationIndication.VALID,
                        inspection.format() + " uzun dönem doğrulama materyali doğrulandı.",
                        null,
                        inspection.details()));
            }
            if (!policyConfiguration.signingCertificateValidityActive()) {
                checks.add(ValidationCheck.of(
                        "SIGNING_CERTIFICATE_VALIDITY_PASSIVE",
                        ValidationIndication.INDETERMINATE,
                        "İmza kriptografik olarak doğrulandı; imzalayan sertifikasının tarih ve güven yolu kontrolü local/test politikasıyla pasif."));
                checks.addAll(passivePolicyChecks(policyConfiguration));
                var main = finalIndication(
                        policyConfiguration,
                        ValidationIndication.INDETERMINATE);
                return new ValidationReport(
                        UUID.randomUUID(),
                        "SIGNATURE",
                        main,
                        ValidationIndication.VALID,
                        ValidationIndication.INDETERMINATE,
                        validationTime,
                        effectivePolicyVersion(policyConfiguration),
                        policyConfiguration.mode(),
                        passivePolicies(policyConfiguration),
                        "NOT_SELECTED",
                        inspection.format(),
                        inspection.level(),
                        signerInfo(inspection.signerCertificate()),
                        List.of(),
                        checks,
                        "İmza kriptografik olarak geçerli; sertifika tarih/güven kontrolü test politikasıyla pasif.");
            }
            var evidenceValidator = new NetworkRevocationDataProvider(
                    HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .connectTimeout(properties.getNetworkTimeout())
                            .build(),
                    properties.getNetworkTimeout(),
                    properties.getMaximumRevocationAge(),
                    properties.getClockSkew());
            var signatureCertificateValidator = inspection.revocationValues().isEmpty()
                    ? certificateValidator
                    : new DefaultCertificateValidator(
                            trustedCertificates,
                            new EmbeddedFirstRevocationDataProvider(
                                    inspection.revocationValues(), evidenceValidator, revocationFallback));
            var certificateResult = signatureCertificateValidator.validate(new CertificateValidationRequest(
                    inspection.signerCertificate(),
                    inspection.embeddedCertificates(),
                    inspection.certificateValidationTime() == null
                            ? validationTime
                            : inspection.certificateValidationTime(),
                    effectivePolicyVersion(policyConfiguration),
                    certificatePolicy));
            checks.addAll(certificateResult.checks());
            checks.addAll(passivePolicyChecks(policyConfiguration));
            var main = finalIndication(
                    policyConfiguration,
                    combine(
                            certificateResult.cryptographicValidity(),
                            certificateResult.turkishQualification()));
            return new ValidationReport(
                    UUID.randomUUID(),
                    "SIGNATURE",
                    main,
                    certificateResult.cryptographicValidity(),
                    certificateResult.turkishQualification(),
                    validationTime,
                    certificateResult.policyVersion(),
                    policyConfiguration.mode(),
                    passivePolicies(policyConfiguration),
                    certificateResult.trustStoreVersion(),
                    inspection.format(),
                    inspection.level(),
                    signerInfo(inspection.signerCertificate()),
                    certificateResult.certificationPath(),
                    checks,
                    summary(
                            main,
                            certificateResult.cryptographicValidity(),
                            certificateResult.turkishQualification()));
        } catch (CadesException exception) {
            return invalidSignatureReport(
                    request.format(), exception.code(), exception.getMessage(),
                    validationTime, policyConfiguration, checks, detectedSigner);
        } catch (XadesException exception) {
            return invalidSignatureReport(
                    "XADES", exception.code(), exception.getMessage(),
                    validationTime, policyConfiguration, checks, detectedSigner);
        } catch (PadesException exception) {
            return invalidSignatureReport(
                    "PADES", exception.code(), exception.getMessage(),
                    validationTime, policyConfiguration, checks, detectedSigner);
        } catch (IllegalStateException exception) {
            return invalidSignatureReport(
                    request.format(), "TRUST_STORE_NOT_CONFIGURED", exception.getMessage(),
                    validationTime, policyConfiguration, checks, detectedSigner);
        }
    }

    private GenericSignatureInspection inspectSignature(
            SignatureValidationApiRequest request,
            byte[] signature,
            SignaturePolicy expectedPolicy,
            java.util.Set<TrustAnchor> tsaRoots,
            boolean certificateValidityCheckActive) {
        var packaging = detectedPackaging(request.format(), signature);
        if (!"AUTO".equals(request.signaturePackaging())
                && !packaging.equals(request.signaturePackaging())) {
            throw new CadesException(
                    "SIGNATURE_PACKAGING_MISMATCH",
                    "Seçilen paketleme türü " + request.signaturePackaging()
                            + ", imzada tespit edilen tür " + packaging + ".");
        }
        return switch (request.format()) {
            case "CADES" -> inspectCades(
                    optionalContent(request),
                    signature,
                    expectedPolicy,
                    tsaRoots,
                    packaging,
                    certificateValidityCheckActive);
            case "XADES" -> inspectXades(
                    optionalContent(request), signature, tsaRoots, packaging);
            case "PADES" -> inspectPades(
                    signature,
                    expectedPolicy,
                    tsaRoots,
                    certificateValidityCheckActive);
            default -> throw new CadesException(
                    "SIGNATURE_FORMAT_UNSUPPORTED",
                    "Desteklenen doğrulama formatları CADES, XADES ve PADES'tir.");
        };
    }

    private GenericSignatureInspection inspectCades(
            byte[] content,
            byte[] signature,
            SignaturePolicy expectedPolicy,
            java.util.Set<TrustAnchor> tsaRoots,
            String packaging,
            boolean certificateValidityCheckActive) {
        var cades = signatureVerifier.verify(
                content,
                signature,
                expectedPolicy,
                tsaRoots,
                certificateValidityCheckActive);
        var parsed = inspector.inspect(signature);
        var revocations = parsed.embeddedRevocationValues().stream()
                .map(value -> new EmbeddedRevocationValue(value.type().name(), value.encodedValue()))
                .toList();
        return new GenericSignatureInspection(
                parsed.signerCertificate(),
                parsed.embeddedCertificates(),
                revocations,
                cades.format(),
                cades.level(),
                cades.timestampGenerationTime(),
                cades.level().startsWith("B-LT"),
                Map.of(
                        "format", cades.format(),
                        "level", cades.level(),
                        "packaging", packaging,
                        "embeddedCertificates", Integer.toString(cades.embeddedCertificateCount()),
                        "embeddedRevocationValues", Integer.toString(cades.embeddedRevocationValueCount()),
                        "archiveTimestamps", Integer.toString(cades.archiveTimestampGenerationTimes().size())));
    }

    private GenericSignatureInspection inspectXades(
            byte[] content,
            byte[] signature,
            java.util.Set<TrustAnchor> tsaRoots,
            String packaging) {
        var xades = xadesVerifier.verify(content, signature, tsaRoots);
        return new GenericSignatureInspection(
                encoded(xades.signerCertificate()),
                xades.embeddedCertificates().stream().map(ValidationService::encoded).toList(),
                List.of(),
                xades.format(),
                xades.level(),
                xades.timestampGenerationTime() == null
                        ? xades.signingTime()
                        : xades.timestampGenerationTime(),
                false,
                Map.of(
                        "format", xades.format(),
                        "level", xades.level(),
                        "packaging", packaging,
                        "embeddedCertificates", Integer.toString(xades.embeddedCertificates().size())));
    }

    private GenericSignatureInspection inspectPades(
            byte[] signature,
            SignaturePolicy expectedPolicy,
            java.util.Set<TrustAnchor> tsaRoots,
            boolean certificateValidityCheckActive) {
        var pades = padesVerifier.verify(
                signature,
                expectedPolicy,
                tsaRoots,
                certificateValidityCheckActive);
        var cades = pades.cades();
        return new GenericSignatureInspection(
                pades.signerCertificate(),
                pades.embeddedCertificates(),
                List.of(),
                "PAdES",
                cades.level(),
                cades.timestampGenerationTime(),
                cades.level().startsWith("B-LT"),
                Map.of(
                        "format", "PAdES",
                        "level", cades.level(),
                        "packaging", "ENVELOPED",
                        "embeddedCertificates", Integer.toString(cades.embeddedCertificateCount()),
                        "embeddedRevocationValues", Integer.toString(cades.embeddedRevocationValueCount())));
    }

    private static byte[] optionalContent(SignatureValidationApiRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            return null;
        }
        return BoundedBase64Decoder.decode(
                request.content(), "content", ApiLimits.MAX_DOCUMENT_BYTES);
    }

    private static String detectedPackaging(String format, byte[] signature) {
        try {
            if ("PADES".equals(format)) {
                return "ENVELOPED";
            }
            if ("CADES".equals(format)) {
                return new org.bouncycastle.cms.CMSSignedData(signature).getSignedContent() == null
                        ? "DETACHED"
                        : "ATTACHED";
            }
            if (!"XADES".equals(format)) {
                return "UNKNOWN";
            }
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(
                    new java.io.ByteArrayInputStream(signature));
            var root = document.getDocumentElement();
            org.w3c.dom.Element signatureRoot;
            if ("Signature".equals(root.getLocalName())
                    && "http://www.w3.org/2000/09/xmldsig#"
                            .equals(root.getNamespaceURI())) {
                signatureRoot = root;
            } else if ("Signatures".equals(root.getLocalName())
                    && "urn:tr:gov:eimza:multi-signature:1"
                            .equals(root.getNamespaceURI())) {
                var signatures = root.getElementsByTagNameNS(
                        "http://www.w3.org/2000/09/xmldsig#",
                        "Signature");
                if (signatures.getLength() == 0) {
                    throw new XadesException(
                            "XADES_SIGNATURE_COUNT_INVALID",
                            "XAdES Çoklu imza kapsayıcısı imza içermiyor.");
                }
                signatureRoot = (org.w3c.dom.Element) signatures.item(0);
            } else {
                return "ENVELOPED";
            }
            var references = signatureRoot.getElementsByTagNameNS(
                    "http://www.w3.org/2000/09/xmldsig#", "Reference");
            for (int index = 0; index < references.getLength(); index++) {
                var reference = (org.w3c.dom.Element) references.item(index);
                var uri = reference.getAttribute("URI");
                if (!reference.hasAttribute("Type")
                        && uri != null
                        && !uri.isBlank()
                        && !uri.startsWith("#")) {
                    return "DETACHED";
                }
                if (uri.startsWith("#") && !reference.hasAttribute("Type")) {
                    return "ENVELOPING";
                }
            }
            return "DETACHED";
        } catch (Exception exception) {
            if ("CADES".equals(format)) {
                throw new CadesException(
                        "CADES_INVALID",
                        "CAdES imza yapısı okunamadı.",
                        exception);
            }
            if ("XADES".equals(format)) {
                throw new XadesException(
                        "XADES_VALIDATION_FAILED",
                        "XAdES imza yapısı okunamadı.",
                        exception);
            }
            throw new CadesException(
                    "SIGNATURE_PACKAGING_DETECTION_FAILED",
                    "İmza paketleme türü tespit edilemedi.",
                    exception);
        }
    }

    private ValidationReport invalidSignatureReport(
            String format,
            String code,
            String message,
            Instant validationTime,
            ValidationPolicyConfigurationResponse policyConfiguration,
            List<ValidationCheck> checks,
            SignerCertificateInfo signer) {
        var indication = "TSA_TRUST_NOT_CONFIGURED".equals(code)
                        || "TRUST_STORE_NOT_CONFIGURED".equals(code)
                ? ValidationIndication.INDETERMINATE
                : ValidationIndication.INVALID;
        checks.add(ValidationCheck.of(code, indication, message));
        return new ValidationReport(
                UUID.randomUUID(),
                "SIGNATURE",
                indication,
                indication,
                ValidationIndication.INDETERMINATE,
                validationTime,
                effectivePolicyVersion(policyConfiguration),
                policyConfiguration.mode(),
                passivePolicies(policyConfiguration),
                "NOT_SELECTED",
                format,
                null,
                signer,
                List.of(),
                checks,
                indication == ValidationIndication.INVALID
                        ? "İmza kriptografik veya biçimsel doğrulamadan geçemedi."
                        : "İmza doğrulaması için gerekli güven kanıtı bulunamadı.");
    }

    private static byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (Exception exception) {
            throw new CadesException(
                    "SIGNER_CERTIFICATE_INVALID",
                    "İmzalayan sertifikası kodlanamadı.",
                    exception);
        }
    }

    private static SignerCertificateInfo signerInfo(byte[] encodedCertificate) {
        try {
            var certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encodedCertificate));
            var attributes = new java.util.HashMap<String, String>();
            for (var rdn : new LdapName(
                            certificate.getSubjectX500Principal().getName())
                    .getRdns()) {
                attributes.putIfAbsent(
                        rdn.getType().toUpperCase(java.util.Locale.ROOT),
                        String.valueOf(rdn.getValue()));
            }
            var identitySerial = first(
                    attributes, "SERIALNUMBER", "2.5.4.5", "OID.2.5.4.5");
            return new SignerCertificateInfo(
                    first(attributes, "CN", "2.5.4.3", "OID.2.5.4.3"),
                    certificate.getSubjectX500Principal().getName(),
                    identitySerial,
                    first(attributes, "O", "2.5.4.10", "OID.2.5.4.10"),
                    first(attributes, "OU", "2.5.4.11", "OID.2.5.4.11"),
                    first(attributes, "C", "2.5.4.6", "OID.2.5.4.6"),
                    certificate.getIssuerX500Principal().getName(),
                    certificate.getSerialNumber().toString(16).toUpperCase(java.util.Locale.ROOT),
                    certificate.getNotBefore().toInstant(),
                    certificate.getNotAfter().toInstant(),
                    certificate.getPublicKey().getAlgorithm(),
                    HexFormat.of().withUpperCase().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(encodedCertificate)),
                    Base64.getEncoder().encodeToString(encodedCertificate));
        } catch (Exception exception) {
            throw new CadesException(
                    "SIGNER_CERTIFICATE_INVALID",
                    "İmzalayan sertifikası bilgileri okunamadı.",
                    exception);
        }
    }

    private SignerCertificateInfo trySignerInfo(String format, byte[] signature) {
        try {
            byte[] encodedCertificate;
            if ("CADES".equals(format)) {
                encodedCertificate = inspector.inspect(signature).signerCertificate();
            } else if ("PADES".equals(format)) {
                try (var document = org.apache.pdfbox.Loader.loadPDF(signature)) {
                    var signatures = document.getSignatureDictionaries();
                    if (signatures.isEmpty()) {
                        return null;
                    }
                    encodedCertificate = inspector.inspect(
                                    signatures.get(signatures.size() - 1)
                                            .getContents(signature))
                            .signerCertificate();
                }
            } else if ("XADES".equals(format)) {
                var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                var document = factory.newDocumentBuilder()
                        .parse(new ByteArrayInputStream(signature));
                var certificates = document.getElementsByTagNameNS(
                        "http://www.w3.org/2000/09/xmldsig#", "X509Certificate");
                if (certificates.getLength() == 0) {
                    return null;
                }
                encodedCertificate = Base64.getMimeDecoder()
                        .decode(certificates.item(0).getTextContent());
            } else {
                return null;
            }
            return signerInfo(encodedCertificate);
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    private static String first(Map<String, String> attributes, String... names) {
        for (var name : names) {
            var value = attributes.get(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record GenericSignatureInspection(
            byte[] signerCertificate,
            List<byte[]> embeddedCertificates,
            List<EmbeddedRevocationValue> revocationValues,
            String format,
            String level,
            Instant certificateValidationTime,
            boolean longTermMaterialVerified,
            Map<String, String> details) {

        private GenericSignatureInspection {
            signerCertificate = signerCertificate.clone();
            embeddedCertificates = embeddedCertificates.stream().map(byte[]::clone).toList();
            revocationValues = List.copyOf(revocationValues);
            details = Map.copyOf(details);
        }
    }

    private SignaturePolicy configuredSignaturePolicy(
            ValidationPolicyConfigurationResponse configuration) {
        if (!configuration.signaturePolicyActive()) {
            return null;
        }
        if (properties.getSignaturePolicyOid() == null
                || properties.getSignaturePolicyOid().isBlank()) {
            return null;
        }
        return new SignaturePolicy(
                properties.getSignaturePolicyOid(),
                properties.getSignaturePolicyDigestAlgorithmOid(),
                BoundedBase64Decoder.decode(
                        properties.getSignaturePolicyDigest(), "signaturePolicyDigest", 128),
                properties.getSignaturePolicyUri());
    }

    private CertificateValidationPolicy effectiveCertificatePolicy(
            ValidationPolicyConfigurationResponse configuration) {
        return new CertificateValidationPolicy(
                properties.getMinimumRsaBits(),
                properties.getMinimumEcBits(),
                true,
                configuration.qcComplianceActive() && properties.isRequireQcCompliance(),
                configuration.certificatePolicyActive()
                        ? java.util.Set.copyOf(properties.getRequiredCertificatePolicyOids())
                        : java.util.Set.of(),
                configuration.revocationActive() && properties.isRevocationRequired(),
                properties.getMaximumRevocationAge(),
                properties.getClockSkew());
    }

    private String effectivePolicyVersion(ValidationPolicyConfigurationResponse configuration) {
        return properties.getPolicyVersion() + "-cfg-" + configuration.version();
    }

    private static ValidationIndication finalIndication(
            ValidationPolicyConfigurationResponse configuration,
            ValidationIndication calculated) {
        return configuration.mode() == ValidationPolicyMode.AUDIT_ONLY
                ? ValidationIndication.INDETERMINATE
                : calculated;
    }

    private static List<String> passivePolicies(
            ValidationPolicyConfigurationResponse configuration) {
        var passive = new ArrayList<String>();
        if (!configuration.qcComplianceActive()) {
            passive.add("QC_COMPLIANCE");
        }
        if (!configuration.certificatePolicyActive()) {
            passive.add("CERTIFICATE_POLICY");
        }
        if (!configuration.revocationActive()) {
            passive.add("REVOCATION_AVAILABILITY");
        }
        if (!configuration.signaturePolicyActive()) {
            passive.add("SIGNATURE_POLICY");
        }
        return List.copyOf(passive);
    }

    private static List<ValidationCheck> passivePolicyChecks(
            ValidationPolicyConfigurationResponse configuration) {
        return passivePolicies(configuration).stream()
                .map(policy -> new ValidationCheck(
                        "POLICY_PASSIVE",
                        ValidationIndication.INDETERMINATE,
                        "Politika pasif; sonuç üzerinde engelleyici olarak uygulanmadı.",
                        null,
                        Map.of("policy", policy, "mode", configuration.mode().name())))
                .toList();
    }

    private Instant validationTime(Instant requested) {
        var now = clock.instant();
        if (requested != null && requested.isAfter(now.plusSeconds(300))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "VALIDATION_TIME_IN_FUTURE",
                    "Doğrulama zamanı gelecekte olamaz.",
                    false);
        }
        return requested == null ? now : requested;
    }

    private static byte[] decodeCertificate(String value, String field) {
        return BoundedBase64Decoder.decode(value, field, ApiLimits.MAX_CERTIFICATE_BYTES);
    }

    private static ValidationIndication combine(
            ValidationIndication cryptographic,
            ValidationIndication qualification) {
        if (cryptographic == ValidationIndication.INVALID
                || qualification == ValidationIndication.INVALID) {
            return ValidationIndication.INVALID;
        }
        if (cryptographic == ValidationIndication.INDETERMINATE
                || qualification == ValidationIndication.INDETERMINATE) {
            return ValidationIndication.INDETERMINATE;
        }
        return ValidationIndication.VALID;
    }

    private static String summary(
            ValidationIndication main,
            ValidationIndication cryptographic,
            ValidationIndication qualification) {
        return "Ana sonuç: " + main
                + "; kriptografik/sertifika sonucu: " + cryptographic
                + "; Türkiye nitelikli imza uygunluğu: " + qualification + ".";
    }
}
