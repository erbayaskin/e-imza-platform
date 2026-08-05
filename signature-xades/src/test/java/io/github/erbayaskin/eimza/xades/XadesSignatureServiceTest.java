package io.github.erbayaskin.eimza.xades;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.Signature;
import java.time.Instant;
import java.util.Date;
import javax.xml.parsers.DocumentBuilderFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;

class XadesSignatureServiceTest {
    @Test
    void producesWellFormedBaselineDetachedXmlWithVerifiedExternalSignature() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var now = Instant.parse("2026-07-29T08:00:00Z");
        var name = new X500Name("CN=XAdES Test");
        var builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.ONE, Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)), name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        var cert = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
        var digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest("document".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var service = new XadesSignatureService();
        var preparation = service.prepareDetached(
                "urn:uuid:test", digest, cert, "RSA_PKCS1_SHA256", now);
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivate());
        signer.update(preparation.signedInfo());
        var result = service.completeBaseline(preparation, signer.sign());

        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(result.encodedSignature()));
        assertEquals("Signature", document.getDocumentElement().getLocalName());
        assertEquals(1, document.getElementsByTagNameNS(
                "http://uri.etsi.org/01903/v1.3.2#", "SignedProperties").getLength());
        assertEquals("B-B", result.level());

        var verification = new XadesSignatureVerifier().verifyDetached(
                "document".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                result.encodedSignature(),
                java.util.Set.of());
        assertEquals("XAdES", verification.format());
        assertEquals("B-B", verification.level());
        assertEquals(cert, verification.signerCertificate());
    }

    @Test
    void producesAndVerifiesAllStandardPackagingModesWithSha384() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var now = Instant.parse("2026-07-29T08:00:00Z");
        var name = new X500Name("CN=XAdES Packaging Test");
        var builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.TWO, Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)), name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        var cert = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
        var content = "<Document><Value>imzalanacak XML</Value></Document>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var digest = java.security.MessageDigest.getInstance("SHA-256").digest(content);
        var service = new XadesSignatureService();

        for (var packaging : new SignaturePackaging[] {
                SignaturePackaging.DETACHED,
                SignaturePackaging.ENVELOPED,
                SignaturePackaging.ENVELOPING}) {
            var preparation = service.prepare(
                    packaging,
                    "urn:uuid:packaging-test",
                    content,
                    "application/xml",
                    digest,
                    cert,
                    "RSA_PKCS1_SHA384",
                    now);
            var signer = Signature.getInstance("SHA384withRSA");
            signer.initSign(keys.getPrivate());
            signer.update(preparation.signedInfo());
            var result = service.completeBaseline(preparation, signer.sign());

            var verification = new XadesSignatureVerifier().verify(
                    packaging == SignaturePackaging.DETACHED ? content : null,
                    result.encodedSignature(),
                    java.util.Set.of());

            assertEquals("XAdES", verification.format(), packaging.name());
            assertEquals("B-B", verification.level(), packaging.name());
        }
    }
    @Test
    void createsAndVerifiesParallelAndCounterSignedXades() throws Exception {
        var now = Instant.parse("2026-07-29T09:00:00Z");
        var content = "multi-xades".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var digest = java.security.MessageDigest.getInstance("SHA-256").digest(content);
        var firstSigner = signer("CN=XAdES First", BigInteger.valueOf(51), now);
        var secondSigner = signer("CN=XAdES Second", BigInteger.valueOf(52), now);
        var counterSigner = signer("CN=XAdES Counter", BigInteger.valueOf(53), now);
        var service = new XadesSignatureService();

        var firstPreparation = service.prepareDetached(
                "urn:uuid:multi-xades",
                digest,
                firstSigner.certificate(),
                "RSA_PKCS1_SHA256",
                now);
        var first = service.completeBaseline(
                firstPreparation,
                sign(firstPreparation.signedInfo(), firstSigner.keys()));

        var parallelPreparation = service.prepareParallel(
                first.encodedSignature(),
                SignaturePackaging.DETACHED,
                "urn:uuid:multi-xades",
                content,
                "application/octet-stream",
                digest,
                secondSigner.certificate(),
                "RSA_PKCS1_SHA256",
                now.plusSeconds(1),
                true);
        var parallel = service.completeBaseline(
                parallelPreparation,
                sign(parallelPreparation.signedInfo(), secondSigner.keys()));
        var parallelDocument = document(parallel.encodedSignature());
        assertEquals(2, parallelDocument.getElementsByTagNameNS(
                "http://www.w3.org/2000/09/xmldsig#", "Signature").getLength());
        assertEquals("B-B", new XadesSignatureVerifier()
                .verifyDetached(content, parallel.encodedSignature(), java.util.Set.of())
                .level());

        var counterPreparation = service.prepareCounterSignature(
                parallel.encodedSignature(),
                0,
                counterSigner.certificate(),
                "RSA_PKCS1_SHA256",
                now.plusSeconds(2),
                true);
        var serial = service.completeBaseline(
                counterPreparation,
                sign(counterPreparation.signedInfo(), counterSigner.keys()));
        var serialDocument = document(serial.encodedSignature());
        assertEquals(3, serialDocument.getElementsByTagNameNS(
                "http://www.w3.org/2000/09/xmldsig#", "Signature").getLength());
        assertEquals(1, serialDocument.getElementsByTagNameNS(
                "http://uri.etsi.org/01903/v1.3.2#", "CounterSignature").getLength());
        assertEquals("B-B", new XadesSignatureVerifier()
                .verifyDetached(content, serial.encodedSignature(), java.util.Set.of())
                .level());
    }

    private static SignerFixture signer(
            String subject, BigInteger serial, Instant now) throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var name = new X500Name(subject);
        var builder = new JcaX509v3CertificateBuilder(
                name,
                serial,
                Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)),
                name,
                keys.getPublic());
        builder.addExtension(
                Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        var certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(
                        new JcaContentSignerBuilder("SHA256withRSA")
                                .build(keys.getPrivate())));
        return new SignerFixture(keys, certificate);
    }

    private static byte[] sign(byte[] value, KeyPair keys) throws Exception {
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivate());
        signer.update(value);
        return signer.sign();
    }

    private static org.w3c.dom.Document document(byte[] value) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(value));
    }

    private record SignerFixture(KeyPair keys, X509Certificate certificate) {
    }

}
