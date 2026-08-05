package io.github.erbayaskin.eimza.xades;

import java.math.BigInteger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import javax.xml.XMLConstants;
import javax.xml.crypto.OctetStreamData;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;
import io.github.erbayaskin.eimza.timestamp.TimestampClient;
import io.github.erbayaskin.eimza.timestamp.TimestampRequest;

/**
 * ETSI EN 319 132 baseline detached XAdES producer using an external signing key.
 * The returned digest is the SHA-256 digest of canonical SignedInfo and is suitable
 * for a PKCS#11 RSA/ECDSA SHA-256 operation.
 */
public final class XadesSignatureService {
    private static final String SHA256_OID = "2.16.840.1.101.3.4.2.1";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String XADES = "http://uri.etsi.org/01903/v1.3.2#";
    private static final String MULTI = "urn:tr:gov:eimza:multi-signature:1";
    private static final String COUNTERSIGNED_SIGNATURE =
            "http://uri.etsi.org/01903#CountersignedSignature";
    private static final Base64.Encoder B64 = Base64.getEncoder();
    private final SecureRandom random = new SecureRandom();

    public XadesSigningPreparation prepareDetached(
            String documentUri,
            byte[] documentDigest,
            X509Certificate certificate,
            String signatureAlgorithm,
            Instant signingTime) {
        return prepare(
                SignaturePackaging.DETACHED,
                documentUri,
                null,
                "application/octet-stream",
                documentDigest,
                certificate,
                signatureAlgorithm,
                signingTime);
    }

    public XadesSigningPreparation prepare(
            SignaturePackaging packaging,
            String documentUri,
            byte[] documentContent,
            String mediaType,
            byte[] documentDigest,
            X509Certificate certificate,
            String signatureAlgorithm,
            Instant signingTime) {
        return prepare(
                packaging,
                documentUri,
                documentContent,
                mediaType,
                documentDigest,
                certificate,
                signatureAlgorithm,
                signingTime,
                true);
    }

    public XadesSigningPreparation prepare(
            SignaturePackaging packaging,
            String documentUri,
            byte[] documentContent,
            String mediaType,
            byte[] documentDigest,
            X509Certificate certificate,
            String signatureAlgorithm,
            Instant signingTime,
            boolean certificateValidityCheckActive) {
        try {
            validate(
                    certificate,
                    signatureAlgorithm,
                    signingTime,
                    certificateValidityCheckActive);
            if (packaging == null
                    || packaging == SignaturePackaging.ATTACHED) {
                throw new XadesException(
                        "XADES_PACKAGING_NOT_ALLOWED",
                        "XAdES için ENVELOPED, ENVELOPING veya DETACHED seçilmelidir.");
            }
            if (documentDigest == null || documentDigest.length != 32) {
                throw new XadesException("INVALID_DOCUMENT_DIGEST", "SHA-256 belge ozeti 32 bayt olmalidir.");
            }
            if (packaging != SignaturePackaging.DETACHED
                    && (documentContent == null || documentContent.length == 0)) {
                throw new XadesException(
                        "XADES_DOCUMENT_CONTENT_REQUIRED",
                        packaging + " XAdES için belge içeriği zorunludur.");
            }
            var id = "Signature-" + java.util.UUID.randomUUID();
            String referenceUri;
            String referenceTransforms;
            byte[] effectiveDocumentDigest;
            String documentObject = null;
            if (packaging == SignaturePackaging.DETACHED) {
                referenceUri = documentUri;
                referenceTransforms = "";
                effectiveDocumentDigest = documentDigest;
            } else if (packaging == SignaturePackaging.ENVELOPED) {
                parse(documentContent);
                referenceUri = "";
                referenceTransforms = """
                        <ds:Transforms><ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"></ds:Transform><ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"></ds:Transform></ds:Transforms>
                        """.strip();
                effectiveDocumentDigest = MessageDigest.getInstance("SHA-256")
                        .digest(canonicalize(documentContent));
            } else {
                referenceUri = "#" + id + "-Document";
                referenceTransforms = """
                        <ds:Transforms><ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"></ds:Transform></ds:Transforms>
                        """.strip();
                documentObject = """
                        <ds:Object xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id="%s-Document" MimeType="%s" Encoding="http://www.w3.org/2000/09/xmldsig#base64">%s</ds:Object>
                        """.formatted(
                                id,
                                xml(mediaType == null ? "application/octet-stream" : mediaType),
                                B64.encodeToString(documentContent)).strip();
                effectiveDocumentDigest = MessageDigest.getInstance("SHA-256")
                        .digest(canonicalize(documentObject.getBytes(StandardCharsets.UTF_8)));
            }
            var certDigest = B64.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            var signedProperties = """
                    <xades:SignedProperties xmlns:xades="http://uri.etsi.org/01903/v1.3.2#" Id="%s-SignedProperties"><xades:SignedSignatureProperties><xades:SigningTime>%s</xades:SigningTime><xades:SigningCertificate><xades:Cert><xades:CertDigest><ds:DigestMethod xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"></ds:DigestMethod><ds:DigestValue xmlns:ds="http://www.w3.org/2000/09/xmldsig#">%s</ds:DigestValue></xades:CertDigest><xades:IssuerSerial><ds:X509IssuerName xmlns:ds="http://www.w3.org/2000/09/xmldsig#">%s</ds:X509IssuerName><ds:X509SerialNumber xmlns:ds="http://www.w3.org/2000/09/xmldsig#">%s</ds:X509SerialNumber></xades:IssuerSerial></xades:Cert></xades:SigningCertificate></xades:SignedSignatureProperties></xades:SignedProperties>
                    """.formatted(
                            id,
                            signingTime,
                            certDigest,
                            xml(certificate.getIssuerX500Principal().getName()),
                            certificate.getSerialNumber()).strip();
            var propertiesDigest = B64.encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(signedProperties.getBytes(StandardCharsets.UTF_8)));
            var signedInfo = """
                    <ds:SignedInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#"><ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"></ds:CanonicalizationMethod><ds:SignatureMethod Algorithm="%s"></ds:SignatureMethod><ds:Reference URI="%s">%s<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"></ds:DigestMethod><ds:DigestValue>%s</ds:DigestValue></ds:Reference><ds:Reference Type="http://uri.etsi.org/01903#SignedProperties" URI="#%s-SignedProperties"><ds:Transforms><ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"></ds:Transform></ds:Transforms><ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"></ds:DigestMethod><ds:DigestValue>%s</ds:DigestValue></ds:Reference></ds:SignedInfo>
                    """.formatted(
                            signatureMethod(signatureAlgorithm),
                            xml(referenceUri),
                            referenceTransforms,
                            B64.encodeToString(effectiveDocumentDigest),
                            id,
                            propertiesDigest).strip();
            var bytes = signedInfo.getBytes(StandardCharsets.UTF_8);
            return new XadesSigningPreparation(
                    bytes,
                    MessageDigest.getInstance(digestName(signatureAlgorithm)).digest(bytes),
                    certificate.getEncoded(),
                    signatureAlgorithm,
                    id,
                    signedProperties,
                    documentUri,
                    B64.encodeToString(effectiveDocumentDigest),
                    signingTime,
                    packaging,
                    documentContent,
                    documentObject,
                    mediaType);
        } catch (XadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XadesException("XADES_PREPARATION_FAILED", "XAdES hazirligi basarisiz.", exception);
        }
    }

    /**
     * Creates an independent XAdES co-signature. Detached and enveloping
     * packaging are supported because adding a sibling to a legacy enveloped
     * signature would invalidate its document reference.
     */
    public XadesSigningPreparation prepareParallel(
            byte[] existingSignature,
            SignaturePackaging packaging,
            String documentUri,
            byte[] documentContent,
            String mediaType,
            byte[] documentDigest,
            X509Certificate certificate,
            String signatureAlgorithm,
            Instant signingTime,
            boolean certificateValidityCheckActive) {
        requireExistingSignature(existingSignature);
        if (packaging == SignaturePackaging.ENVELOPED) {
            throw new XadesException(
                    "XADES_PARALLEL_ENVELOPED_NOT_SUPPORTED",
                    "Paralel XAdES için DETACHED veya ENVELOPING seçilmelidir.");
        }
        var prepared = prepare(
                packaging,
                documentUri,
                documentContent,
                mediaType,
                documentDigest,
                certificate,
                signatureAlgorithm,
                signingTime,
                certificateValidityCheckActive);
        return multi(prepared, existingSignature, MultiSignatureType.PARALLEL, 0);
    }

    /**
     * Creates an ETSI XAdES CounterSignature whose reference covers the
     * selected parent ds:SignatureValue.
     */
    public XadesSigningPreparation prepareCounterSignature(
            byte[] existingSignature,
            int targetSignatureIndex,
            X509Certificate certificate,
            String signatureAlgorithm,
            Instant signingTime,
            boolean certificateValidityCheckActive) {
        try {
            requireExistingSignature(existingSignature);
            var document = parse(existingSignature);
            var signatures = document.getElementsByTagNameNS(DS, "Signature");
            if (targetSignatureIndex < 0 || targetSignatureIndex >= signatures.getLength()) {
                throw new XadesException(
                        "XADES_TARGET_SIGNATURE_NOT_FOUND",
                        "Seri imza için hedef XAdES imza indeksi bulunamadı.");
            }
            var target = (org.w3c.dom.Element) signatures.item(targetSignatureIndex);
            var values = target.getElementsByTagNameNS(DS, "SignatureValue");
            if (values.getLength() == 0) {
                throw new XadesException(
                        "XADES_SIGNATURE_VALUE_MISSING",
                        "Hedef XAdES SignatureValue bulunamadı.");
            }
            var signatureValue = (org.w3c.dom.Element) values.item(0);
            var signatureValueId = signatureValue.getAttribute("Id");
            if (signatureValueId == null || signatureValueId.isBlank()) {
                throw new XadesException(
                        "XADES_SIGNATURE_VALUE_ID_MISSING",
                        "Seri imza için hedef SignatureValue Id alanı zorunludur.");
            }
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalize(serializeNode(signatureValue)));
            var base = prepare(
                    SignaturePackaging.DETACHED,
                    "#" + signatureValueId,
                    null,
                    "application/xml",
                    digest,
                    certificate,
                    signatureAlgorithm,
                    signingTime,
                    certificateValidityCheckActive);
            var uri = "#" + signatureValueId;
            var signedInfo = new String(base.signedInfo(), StandardCharsets.UTF_8);
            var reference = "<ds:Reference URI=\"" + xml(uri) + "\">";
            var replacement = "<ds:Reference Type=\"" + COUNTERSIGNED_SIGNATURE
                    + "\" URI=\"" + xml(uri)
                    + "\"><ds:Transforms><ds:Transform Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"></ds:Transform></ds:Transforms>";
            if (!signedInfo.contains(reference)) {
                throw new XadesException(
                        "XADES_COUNTERSIGN_REFERENCE_INVALID",
                        "Seri imza referansı hazırlanamadı.");
            }
            var adjusted = signedInfo.replace(reference, replacement)
                    .getBytes(StandardCharsets.UTF_8);
            var prepared = new XadesSigningPreparation(
                    adjusted,
                    MessageDigest.getInstance(digestName(signatureAlgorithm)).digest(adjusted),
                    base.signerCertificate(),
                    base.signatureAlgorithm(),
                    base.signatureId(),
                    base.signedProperties(),
                    base.documentUri(),
                    base.documentDigestBase64(),
                    base.signingTime(),
                    base.packaging(),
                    base.documentContent(),
                    base.documentObject(),
                    base.mediaType(),
                    existingSignature,
                    MultiSignatureType.SERIAL,
                    targetSignatureIndex);
            return prepared;
        } catch (XadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XadesException(
                    "XADES_COUNTERSIGN_PREPARATION_FAILED",
                    "XAdES seri imza hazırlığı başarısız.",
                    exception);
        }
    }

    public XadesSignatureResult completeBaseline(
            XadesSigningPreparation preparation, byte[] rawSignature) {
        verify(preparation, rawSignature);
        return new XadesSignatureResult(assemble(preparation, rawSignature, null), "B-B", null, null);
    }

    public XadesSignatureResult completeWithTimestamp(
            XadesSigningPreparation preparation,
            byte[] rawSignature,
            TimestampClient timestampClient,
            String timestampPolicyOid) {
        try {
            verify(preparation, rawSignature);
            var signatureValue = signatureValue(preparation.signatureId(), rawSignature);
            var imprint = MessageDigest.getInstance("SHA-256")
                    .digest(signatureValue.getBytes(StandardCharsets.UTF_8));
            var response = timestampClient.timestamp(new TimestampRequest(
                    SHA256_OID,
                    imprint,
                    new BigInteger(128, random).setBit(127),
                    timestampPolicyOid));
            var unsigned = """
                    <xades:UnsignedProperties><xades:UnsignedSignatureProperties><xades:SignatureTimeStamp><ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/><xades:EncapsulatedTimeStamp>%s</xades:EncapsulatedTimeStamp></xades:SignatureTimeStamp></xades:UnsignedSignatureProperties></xades:UnsignedProperties>
                    """.formatted(B64.encodeToString(response.encodedToken())).strip();
            return new XadesSignatureResult(
                    assemble(preparation, rawSignature, unsigned),
                    "B-T",
                    response.generationTime(),
                    response.policyOid());
        } catch (XadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XadesException("XADES_TIMESTAMP_FAILED", "XAdES B-T zaman damgasi basarisiz.", exception);
        }
    }

    private static byte[] assemble(
            XadesSigningPreparation preparation, byte[] rawSignature, String unsignedProperties) {
        var signatureXml = """
                <?xml version="1.0" encoding="UTF-8"?><ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#" xmlns:xades="http://uri.etsi.org/01903/v1.3.2#" Id="%s">%s%s<ds:KeyInfo><ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data></ds:KeyInfo><ds:Object><xades:QualifyingProperties Target="#%s">%s%s</xades:QualifyingProperties></ds:Object></ds:Signature>
                """.formatted(
                        preparation.signatureId(),
                        new String(preparation.signedInfo(), StandardCharsets.UTF_8),
                        signatureValue(preparation.signatureId(), rawSignature),
                        B64.encodeToString(preparation.signerCertificate()),
                        preparation.signatureId(),
                        preparation.signedProperties(),
                        unsignedProperties == null ? "" : unsignedProperties).strip();
        if (preparation.multiSignatureType() == MultiSignatureType.SERIAL) {
            return attachCounterSignature(
                    preparation,
                    signatureXml.getBytes(StandardCharsets.UTF_8));
        }
        if (preparation.multiSignatureType() == MultiSignatureType.PARALLEL) {
            return mergeParallel(
                    preparation.existingSignature(),
                    signatureXml.getBytes(StandardCharsets.UTF_8));
        }
        if (preparation.packaging() == SignaturePackaging.ENVELOPING) {
            signatureXml = signatureXml.replace(
                    "<ds:Object><xades:QualifyingProperties",
                    preparation.documentObject()
                            + "<ds:Object><xades:QualifyingProperties");
        }
        if (preparation.packaging() != SignaturePackaging.ENVELOPED) {
            return signatureXml.getBytes(StandardCharsets.UTF_8);
        }
        try {
            var document = parse(preparation.documentContent());
            var signature = parse(signatureXml.getBytes(StandardCharsets.UTF_8))
                    .getDocumentElement();
            document.getDocumentElement().appendChild(document.importNode(signature, true));
            return serialize(document);
        } catch (Exception exception) {
            throw new XadesException(
                    "XADES_ENVELOPED_ASSEMBLY_FAILED",
                    "Enveloped XAdES belgesi oluşturulamadı.",
                    exception);
        }
    }

    private static byte[] mergeParallel(byte[] existingSignature, byte[] newSignature) {
        try {
            var existing = parse(existingSignature);
            var added = parse(newSignature);
            if (MULTI.equals(existing.getDocumentElement().getNamespaceURI())
                    && "Signatures".equals(existing.getDocumentElement().getLocalName())) {
                existing.getDocumentElement().appendChild(
                        existing.importNode(added.getDocumentElement(), true));
                return serialize(existing);
            }
            var factory = secureFactory();
            var merged = factory.newDocumentBuilder().newDocument();
            var root = merged.createElementNS(MULTI, "eimza:Signatures");
            root.setAttributeNS(
                    XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                    "xmlns:eimza",
                    MULTI);
            merged.appendChild(root);
            root.appendChild(merged.importNode(existing.getDocumentElement(), true));
            root.appendChild(merged.importNode(added.getDocumentElement(), true));
            return serialize(merged);
        } catch (Exception exception) {
            throw new XadesException(
                    "XADES_PARALLEL_ASSEMBLY_FAILED",
                    "Paralel XAdES artifact'i oluşturulamadı.",
                    exception);
        }
    }

    private static byte[] attachCounterSignature(
            XadesSigningPreparation preparation, byte[] newSignature) {
        try {
            var existing = parse(preparation.existingSignature());
            var signatures = existing.getElementsByTagNameNS(DS, "Signature");
            if (preparation.targetSignatureIndex() >= signatures.getLength()) {
                throw new XadesException(
                        "XADES_TARGET_SIGNATURE_NOT_FOUND",
                        "Seri imza için hedef XAdES imzası bulunamadı.");
            }
            var target = (org.w3c.dom.Element)
                    signatures.item(preparation.targetSignatureIndex());
            var properties = target.getElementsByTagNameNS(XADES, "QualifyingProperties");
            if (properties.getLength() == 0) {
                throw new XadesException(
                        "XADES_QUALIFYING_PROPERTIES_MISSING",
                        "Hedef imzanın XAdES QualifyingProperties alanı bulunamadı.");
            }
            var qualifying = (org.w3c.dom.Element) properties.item(0);
            var unsignedPropertiesNodes =
                    qualifying.getElementsByTagNameNS(XADES, "UnsignedProperties");
            org.w3c.dom.Element unsignedProperties;
            if (unsignedPropertiesNodes.getLength() == 0) {
                unsignedProperties = existing.createElementNS(
                        XADES, "xades:UnsignedProperties");
                qualifying.appendChild(unsignedProperties);
            } else {
                unsignedProperties =
                        (org.w3c.dom.Element) unsignedPropertiesNodes.item(0);
            }
            var unsignedSignatureNodes = unsignedProperties.getElementsByTagNameNS(
                    XADES, "UnsignedSignatureProperties");
            org.w3c.dom.Element unsignedSignatureProperties;
            if (unsignedSignatureNodes.getLength() == 0) {
                unsignedSignatureProperties = existing.createElementNS(
                        XADES, "xades:UnsignedSignatureProperties");
                unsignedProperties.appendChild(unsignedSignatureProperties);
            } else {
                unsignedSignatureProperties =
                        (org.w3c.dom.Element) unsignedSignatureNodes.item(0);
            }
            var counterSignature = existing.createElementNS(
                    XADES, "xades:CounterSignature");
            var added = parse(newSignature);
            counterSignature.appendChild(
                    existing.importNode(added.getDocumentElement(), true));
            unsignedSignatureProperties.appendChild(counterSignature);
            return serialize(existing);
        } catch (XadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XadesException(
                    "XADES_COUNTERSIGN_ASSEMBLY_FAILED",
                    "XAdES seri imza artifact'i oluşturulamadı.",
                    exception);
        }
    }

    private static XadesSigningPreparation multi(
            XadesSigningPreparation preparation,
            byte[] existingSignature,
            MultiSignatureType type,
            int targetSignatureIndex) {
        return new XadesSigningPreparation(
                preparation.signedInfo(),
                preparation.digestToSign(),
                preparation.signerCertificate(),
                preparation.signatureAlgorithm(),
                preparation.signatureId(),
                preparation.signedProperties(),
                preparation.documentUri(),
                preparation.documentDigestBase64(),
                preparation.signingTime(),
                preparation.packaging(),
                preparation.documentContent(),
                preparation.documentObject(),
                preparation.mediaType(),
                existingSignature,
                type,
                targetSignatureIndex);
    }

    private static void requireExistingSignature(byte[] existingSignature) {
        if (existingSignature == null || existingSignature.length == 0) {
            throw new XadesException(
                    "EXISTING_SIGNATURE_REQUIRED",
                    "Paralel veya seri XAdES için önceki imza artifact'i zorunludur.");
        }
    }

    private static String signatureValue(String id, byte[] rawSignature) {
        return "<ds:SignatureValue xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" Id=\"" + id + "-SignatureValue\">"
                + B64.encodeToString(rawSignature) + "</ds:SignatureValue>";
    }

    private static void verify(XadesSigningPreparation preparation, byte[] rawSignature) {
        try {
            var certificate = (X509Certificate) java.security.cert.CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(new java.io.ByteArrayInputStream(preparation.signerCertificate()));
            var verifier = Signature.getInstance(jcaName(preparation.signatureAlgorithm()));
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(preparation.signedInfo());
            if (!verifier.verify(rawSignature)) {
                throw new XadesException("CARD_SIGNATURE_INVALID", "Kart imzasi XAdES SignedInfo ile eslesmiyor.");
            }
        } catch (XadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XadesException("XADES_COMPLETION_FAILED", "XAdES tamamlama basarisiz.", exception);
        }
    }

    private static void validate(
            X509Certificate certificate,
            String algorithm,
            Instant at,
            boolean certificateValidityCheckActive) throws Exception {
        if (certificateValidityCheckActive) {
            certificate.checkValidity(java.util.Date.from(at));
        }
        var required = algorithm.startsWith("RSA") ? "RSA" : "EC";
        if (!required.equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())) {
            throw new XadesException("SIGNATURE_KEY_MISMATCH", "Sertifika anahtar turu algoritmayla eslesmiyor.");
        }
    }

    private static String signatureMethod(String algorithm) {
        return switch (algorithm) {
            case "RSA_PKCS1_SHA256" -> "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
            case "RSA_PKCS1_SHA384" -> "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384";
            case "RSA_PKCS1_SHA512" -> "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512";
            case "ECDSA_SHA256" -> "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256";
            case "ECDSA_SHA384" -> "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384";
            case "ECDSA_SHA512" -> "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512";
            default -> throw new XadesException("ALGORITHM_NOT_ALLOWED", "Desteklenmeyen imza algoritmasi.");
        };
    }

    private static String jcaName(String algorithm) {
        return switch (algorithm) {
            case "RSA_PKCS1_SHA256" -> "SHA256withRSA";
            case "RSA_PKCS1_SHA384" -> "SHA384withRSA";
            case "RSA_PKCS1_SHA512" -> "SHA512withRSA";
            case "ECDSA_SHA256" -> "SHA256withECDSA";
            case "ECDSA_SHA384" -> "SHA384withECDSA";
            case "ECDSA_SHA512" -> "SHA512withECDSA";
            default -> throw new XadesException("ALGORITHM_NOT_ALLOWED", "Desteklenmeyen imza algoritmasi.");
        };
    }

    private static String digestName(String algorithm) {
        if (algorithm.endsWith("_SHA256")) return "SHA-256";
        if (algorithm.endsWith("_SHA384")) return "SHA-384";
        if (algorithm.endsWith("_SHA512")) return "SHA-512";
        throw new XadesException("ALGORITHM_NOT_ALLOWED", "Desteklenmeyen imza algoritması.");
    }

    private static org.w3c.dom.Document parse(byte[] xml) throws Exception {
        return secureFactory().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static byte[] canonicalize(byte[] xml) throws Exception {
        var method = XMLSignatureFactory.getInstance("DOM").newCanonicalizationMethod(
                CanonicalizationMethod.EXCLUSIVE,
                (C14NMethodParameterSpec) null);
        var transformed = method.transform(
                new OctetStreamData(new ByteArrayInputStream(xml)), null);
        if (!(transformed instanceof OctetStreamData stream)) {
            throw new XadesException(
                    "XADES_CANONICALIZATION_FAILED",
                    "XML canonicalization çıktısı okunamadı.");
        }
        return stream.getOctetStream().readAllBytes();
    }

    private static byte[] serializeNode(org.w3c.dom.Node node) throws Exception {
        var factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        var output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(node), new StreamResult(output));
        return output.toByteArray();
    }

    private static byte[] serialize(org.w3c.dom.Document document) throws Exception {
        var factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        var output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toByteArray();
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
