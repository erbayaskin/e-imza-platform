package io.github.erbayaskin.eimza.validation;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import io.github.erbayaskin.eimza.core.model.ValidationIndication;

public class DefaultCertificateValidator implements CertificateValidator {

    private static final String QC_STATEMENTS_OID = "1.3.6.1.5.5.7.1.3";
    private static final String QC_COMPLIANCE_OID = "0.4.0.1862.1.1";
    private final TrustedCertificateProvider trustedCertificates;
    private final RevocationDataProvider revocationData;

    public DefaultCertificateValidator(
            TrustedCertificateProvider trustedCertificates,
            RevocationDataProvider revocationData) {
        this.trustedCertificates = trustedCertificates;
        this.revocationData = revocationData;
    }

    @Override
    public CertificateValidationResult validate(CertificateValidationRequest request) {
        var checks = new ArrayList<ValidationCheck>();
        var pathSubjects = new ArrayList<String>();
        var snapshot = trustedCertificates.snapshotAt(request.validationTime());
        try {
            var target = parse(request.certificate());
            checkAlgorithmAndUsage(target, request.policy(), checks);
            var path = buildPath(target, request, snapshot);
            var certificates = new ArrayList<X509Certificate>();
            for (var certificate : path.getCertPath().getCertificates()) {
                certificates.add((X509Certificate) certificate);
            }
            certificates.add(path.getTrustAnchor().getTrustedCert());
            certificates.forEach(certificate ->
                    pathSubjects.add(certificate.getSubjectX500Principal().getName()));
            checks.add(ValidationCheck.of(
                    "CERT_PATH_VALID",
                    ValidationIndication.VALID,
                    "Sertifika yolu seçilen tarih ve güven deposu sürümünde kuruldu."));
            checkQualification(target, request.policy(), checks);
            checkRevocation(certificates, request, checks);
        } catch (java.security.cert.CertPathBuilderException exception) {
            checks.add(ValidationCheck.of(
                    "TRUST_ANCHOR_NOT_FOUND",
                    ValidationIndication.INDETERMINATE,
                    "Sertifika için güvenilir köke ulaşan yol kurulamadı."));
        } catch (Exception exception) {
            checks.add(ValidationCheck.of(
                    "CERTIFICATE_PROCESSING_ERROR",
                    ValidationIndication.INVALID,
                    "Sertifika yapısı veya doğrulama girdisi geçersiz."));
        }
        var cryptographic = aggregate(checks, false);
        var qualification = aggregate(checks, true);
        return new CertificateValidationResult(
                cryptographic,
                qualification,
                request.validationTime(),
                request.policyVersion(),
                snapshot.version(),
                pathSubjects,
                checks);
    }

    private PKIXCertPathBuilderResult buildPath(
            X509Certificate target,
            CertificateValidationRequest request,
            TrustedCertificateProvider.TrustedCertificateSnapshot snapshot) throws Exception {
        var roots = snapshot.certificates().stream()
                .filter(item -> item.type() == TrustedCertificateProvider.TrustedCertificateType.ROOT)
                .map(item -> new TrustAnchor(item.certificate(), null))
                .collect(java.util.stream.Collectors.toSet());
        if (roots.isEmpty()) {
            throw new java.security.cert.CertPathBuilderException("Güven kökü yok.");
        }
        var candidates = new ArrayList<X509Certificate>();
        candidates.add(target);
        for (var encoded : request.intermediateCertificates()) {
            candidates.add(parse(encoded));
        }
        snapshot.certificates().stream()
                .filter(item -> item.type() == TrustedCertificateProvider.TrustedCertificateType.INTERMEDIATE)
                .map(TrustedCertificateProvider.TrustedCertificate::certificate)
                .forEach(candidates::add);
        var selector = new X509CertSelector();
        selector.setCertificate(target);
        var parameters = new PKIXBuilderParameters(roots, selector);
        parameters.setRevocationEnabled(false);
        parameters.setDate(java.util.Date.from(request.validationTime()));
        parameters.addCertStore(CertStore.getInstance(
                "Collection", new CollectionCertStoreParameters(candidates)));
        return (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(parameters);
    }

    private void checkRevocation(
            List<X509Certificate> path,
            CertificateValidationRequest request,
            List<ValidationCheck> checks) throws Exception {
        for (int index = 0; index < path.size() - 1; index++) {
            var certificate = path.get(index);
            var issuer = path.get(index + 1);
            var evidence = revocationData.resolve(certificate, issuer, request.validationTime());
            var evidenceDigest = evidence.encodedEvidence().length == 0
                    ? null
                    : HexFormat.of().withUpperCase().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(evidence.encodedEvidence()));
            var details = Map.of(
                    "subject", certificate.getSubjectX500Principal().getName(),
                    "sourceType", evidence.sourceType(),
                    "sourceUri", evidence.sourceUri() == null ? "" : evidence.sourceUri());
            switch (evidence.status()) {
                case GOOD -> checks.add(new ValidationCheck(
                        "CERT_STATUS_GOOD",
                        ValidationIndication.VALID,
                        "Sertifika iptal edilmemiş olarak doğrulandı.",
                        evidenceDigest,
                        details));
                case REVOKED -> checks.add(new ValidationCheck(
                        "CERT_REVOKED",
                        ValidationIndication.INVALID,
                        "Sertifika doğrulama zamanında iptal edilmiş.",
                        evidenceDigest,
                        details));
                case UNKNOWN -> checks.add(new ValidationCheck(
                        "CERT_STATUS_UNKNOWN",
                        ValidationIndication.INDETERMINATE,
                        "İptal servisi sertifika durumunu bilmiyor.",
                        evidenceDigest,
                        details));
                case UNAVAILABLE -> checks.add(new ValidationCheck(
                        "REVOCATION_SERVICE_UNAVAILABLE",
                        request.policy().revocationRequired()
                                ? ValidationIndication.INDETERMINATE
                                : ValidationIndication.VALID,
                        "Uygun ve güncel iptal kanıtı elde edilemedi.",
                        evidenceDigest,
                        details));
            }
        }
    }

    private static void checkAlgorithmAndUsage(
            X509Certificate certificate,
            CertificateValidationPolicy policy,
            List<ValidationCheck> checks) {
        var publicKey = certificate.getPublicKey();
        var allowed = true;
        if (publicKey instanceof java.security.interfaces.RSAPublicKey rsa) {
            allowed = rsa.getModulus().bitLength() >= policy.minimumRsaBits();
        } else if (publicKey instanceof java.security.interfaces.ECPublicKey ec) {
            allowed = ec.getParams().getCurve().getField().getFieldSize() >= policy.minimumEcBits();
        }
        if (certificate.getSigAlgName().toUpperCase().contains("SHA1")) {
            allowed = false;
        }
        checks.add(ValidationCheck.of(
                "ALGORITHM_POLICY",
                allowed ? ValidationIndication.VALID : ValidationIndication.INVALID,
                allowed ? "Sertifika algoritmaları politikaya uygun." : "Sertifika algoritmaları politikaya uygun değil."));
        var usage = certificate.getKeyUsage();
        var usageAllowed = !policy.requireDigitalSignatureKeyUsage()
                || usage == null
                || (usage.length > 1 && (usage[0] || usage[1]));
        checks.add(ValidationCheck.of(
                "KEY_USAGE",
                usageAllowed ? ValidationIndication.VALID : ValidationIndication.INVALID,
                usageAllowed ? "Sertifika imza kullanımına izin veriyor." : "Sertifika imza kullanımına izin vermiyor."));
    }

    private static void checkQualification(
            X509Certificate certificate,
            CertificateValidationPolicy policy,
            List<ValidationCheck> checks) throws Exception {
        if (policy.requireQcCompliance()) {
            var compliant = hasQcCompliance(certificate);
            checks.add(ValidationCheck.of(
                    "QC_COMPLIANCE",
                    compliant ? ValidationIndication.VALID : ValidationIndication.INVALID,
                    compliant ? "QC compliance ifadesi bulundu." : "QC compliance ifadesi bulunamadı."));
        }
        if (!policy.requiredCertificatePolicyOids().isEmpty()) {
            var actualPolicies = certificatePolicyOids(certificate);
            var matched = actualPolicies.stream().anyMatch(policy.requiredCertificatePolicyOids()::contains);
            checks.add(ValidationCheck.of(
                    "CERTIFICATE_POLICY",
                    matched ? ValidationIndication.VALID : ValidationIndication.INVALID,
                    matched ? "Sertifika politikası izin verilen OID ile eşleşti." : "Sertifika politika OID değeri izinli değil."));
        }
    }

    private static boolean hasQcCompliance(X509Certificate certificate) throws Exception {
        var encoded = certificate.getExtensionValue(QC_STATEMENTS_OID);
        if (encoded == null) {
            return false;
        }
        var statements = ASN1Sequence.getInstance(
                ASN1OctetString.getInstance(encoded).getOctets());
        for (var item : statements) {
            var statement = ASN1Sequence.getInstance(item);
            if (statement.size() > 0
                    && QC_COMPLIANCE_OID.equals(
                            org.bouncycastle.asn1.ASN1ObjectIdentifier.getInstance(
                                            statement.getObjectAt(0))
                                    .getId())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> certificatePolicyOids(X509Certificate certificate) throws Exception {
        var encoded = certificate.getExtensionValue("2.5.29.32");
        if (encoded == null) {
            return Set.of();
        }
        var policies = CertificatePolicies.getInstance(
                ASN1OctetString.getInstance(encoded).getOctets());
        return java.util.Arrays.stream(policies.getPolicyInformation())
                .map(item -> item.getPolicyIdentifier().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static X509Certificate parse(byte[] encoded) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static ValidationIndication aggregate(
            List<ValidationCheck> checks,
            boolean qualification) {
        var relevant = qualification
                ? checks.stream().filter(check -> check.code().equals("QC_COMPLIANCE")
                        || check.code().equals("CERTIFICATE_POLICY")).toList()
                : checks.stream().filter(check -> !check.code().equals("QC_COMPLIANCE")
                        && !check.code().equals("CERTIFICATE_POLICY")).toList();
        if (relevant.stream().anyMatch(check -> check.indication() == ValidationIndication.INVALID)) {
            return ValidationIndication.INVALID;
        }
        if (relevant.stream().anyMatch(check -> check.indication() == ValidationIndication.INDETERMINATE)) {
            return ValidationIndication.INDETERMINATE;
        }
        return relevant.isEmpty() ? ValidationIndication.INDETERMINATE : ValidationIndication.VALID;
    }
}
