package io.github.erbayaskin.eimza.xades;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.crypto.OctetStreamData;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import io.github.erbayaskin.eimza.timestamp.TimestampTokenVerifier;

/** Secure verifier for the detached baseline XAdES documents produced by this module. */
public final class XadesSignatureVerifier {
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String XADES = "http://uri.etsi.org/01903/v1.3.2#";
    private static final String SHA256_OID = "2.16.840.1.101.3.4.2.1";

    public XadesVerificationResult verifyDetached(
            byte[] content, byte[] encodedSignature, Set<TrustAnchor> tsaTrustAnchors) {
        if (content == null) {
            throw new XadesException(
                    "DETACHED_CONTENT_REQUIRED",
                    "Detached XAdES doğrulaması için orijinal içerik zorunludur.");
        }
        return verify(content, encodedSignature, tsaTrustAnchors);
    }

    public XadesVerificationResult verify(
            byte[] content, byte[] encodedSignature, Set<TrustAnchor> tsaTrustAnchors) {
        try {
            var document = parse(encodedSignature);
            var signatureNodes = document.getElementsByTagNameNS(DS, "Signature");
            if (signatureNodes.getLength() == 0) {
                throw new XadesException(
                        "XADES_SIGNATURE_COUNT_INVALID",
                        "XAdES belgesi en az bir imza içermelidir.");
            }
            var certificates = new java.util.ArrayList<X509Certificate>();
            VerifiedSignature first = null;
            for (int index = 0; index < signatureNodes.getLength(); index++) {
                var verified = verifyOne(
                        document,
                        (Element) signatureNodes.item(index),
                        content,
                        tsaTrustAnchors);
                certificates.add(verified.certificate());
                if (first == null) {
                    first = verified;
                }
            }
            return new XadesVerificationResult(
                    "XAdES",
                    first.level(),
                    first.certificate(),
                    certificates,
                    first.signingTime(),
                    first.timestampTime());
        } catch (XadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XadesException(
                    "XADES_VALIDATION_FAILED",
                    "XAdES doğrulaması başarısız.",
                    exception);
        }
    }

    private static VerifiedSignature verifyOne(
            org.w3c.dom.Document document,
            Element signatureElement,
            byte[] content,
            Set<TrustAnchor> tsaTrustAnchors) throws Exception {
        var certificate = certificate(signatureElement);
        var context = new DOMValidateContext(certificate.getPublicKey(), signatureElement);
        context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
        registerIds(document.getDocumentElement(), context);
        var signedProperties = ownedElement(signatureElement, XADES, "SignedProperties");
        if (signedProperties == null) {
            throw new XadesException(
                    "XADES_SIGNED_PROPERTIES_MISSING",
                    "XAdES SignedProperties bulunamadı.");
        }
        signedProperties.setIdAttribute("Id", true);
        var factory = XMLSignatureFactory.getInstance("DOM");
        var defaultDereferencer = factory.getURIDereferencer();
        context.setURIDereferencer((reference, cryptoContext) -> {
            var uri = reference.getURI();
            if (uri != null && !uri.isBlank() && !uri.startsWith("#")) {
                if (content == null) {
                    throw new javax.xml.crypto.URIReferenceException(
                            "Detached XAdES içeriği sağlanmadı.");
                }
                return new OctetStreamData(new ByteArrayInputStream(content), uri, null);
            }
            return defaultDereferencer.dereference(reference, cryptoContext);
        });
        var xmlSignature = factory.unmarshalXMLSignature(context);
        if (!xmlSignature.validate(context)) {
            throw new XadesException(
                    "XADES_SIGNATURE_INVALID",
                    "XAdES imza değeri veya imzalı referans özetlerinden biri geçersiz.");
        }
        var signingTimeElement = ownedElement(signatureElement, XADES, "SigningTime");
        if (signingTimeElement == null) {
            throw new XadesException(
                    "XADES_SIGNING_TIME_MISSING",
                    "XAdES SigningTime bulunamadı.");
        }
        var signingTime = Instant.parse(signingTimeElement.getTextContent().trim());
        var timestampElement = ownedElement(
                signatureElement, XADES, "EncapsulatedTimeStamp");
        Instant timestampTime = null;
        var level = "B-B";
        if (timestampElement != null) {
            level = "B-T";
            var signatureValue = ownedElement(signatureElement, DS, "SignatureValue");
            var serialized = "<ds:SignatureValue xmlns:ds=\"" + DS + "\" Id=\""
                    + signatureValue.getAttribute("Id") + "\">"
                    + signatureValue.getTextContent().trim()
                    + "</ds:SignatureValue>";
            var imprint = MessageDigest.getInstance("SHA-256")
                    .digest(serialized.getBytes(StandardCharsets.UTF_8));
            var response = new TimestampTokenVerifier().verify(
                    Base64.getMimeDecoder().decode(timestampElement.getTextContent()),
                    SHA256_OID,
                    imprint,
                    null,
                    null,
                    "embedded-xades",
                    tsaTrustAnchors);
            timestampTime = response.generationTime();
        }
        return new VerifiedSignature(
                certificate, signingTime, timestampTime, level);
    }

    private static Element ownedElement(
            Element signature, String namespace, String localName) {
        return ownedElementRecursive(signature, signature, namespace, localName);
    }

    private static Element ownedElementRecursive(
            Element root,
            Element current,
            String namespace,
            String localName) {
        for (var child = current.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            if (!(child instanceof Element element)) {
                continue;
            }
            if (element != root
                    && DS.equals(element.getNamespaceURI())
                    && "Signature".equals(element.getLocalName())) {
                continue;
            }
            if (namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                return element;
            }
            var nested = ownedElementRecursive(root, element, namespace, localName);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static org.w3c.dom.Document parse(byte[] xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static X509Certificate certificate(Element signature) throws Exception {
        var certificateElement = ownedElement(signature, DS, "X509Certificate");
        if (certificateElement == null) {
            throw new XadesException(
                    "XADES_SIGNER_CERTIFICATE_INVALID",
                    "XAdES imzalayan sertifikası bulunamadı.");
        }
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(
                        Base64.getMimeDecoder().decode(
                                certificateElement.getTextContent())));
    }

    private record VerifiedSignature(
            X509Certificate certificate,
            Instant signingTime,
            Instant timestampTime,
            String level) {
    }

    private static void registerIds(
            Element element, DOMValidateContext context) {
        if (element.hasAttribute("Id")) {
            element.setIdAttribute("Id", true);
            context.setIdAttributeNS(element, null, "Id");
        }
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                registerIds(childElement, context);
            }
        }
    }
}
