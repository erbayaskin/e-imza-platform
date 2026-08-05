package io.github.erbayaskin.eimza.pades;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Date;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.cades.CadesSignatureAlgorithm;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;

class PadesSignatureServiceTest {
    @Test
    void producesPdfWithVerifiableEtsiCadesDetachedSignature() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        var keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var now = Instant.parse("2026-07-29T08:00:00Z");
        var name = new X500Name("CN=PAdES Test");
        var builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.ONE, Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)), name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        var cert = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
        var service = new PadesSignatureService();
        var preparation = service.prepare(pdf, cert, CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null, now, "PAdES Test", "approval");
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivate());
        signer.update(preparation.cmsPreparation().signedAttributes());
        var result = service.completeBaseline(preparation, signer.sign());

        try (var signed = Loader.loadPDF(result.encodedPdf())) {
            assertEquals(1, signed.getSignatureDictionaries().size());
            var pdfSignature = signed.getSignatureDictionaries().getFirst();
            var signedContent = pdfSignature.getSignedContent(result.encodedPdf());
            var cms = pdfSignature.getContents(result.encodedPdf());
            var signedData = new CMSSignedData(new CMSProcessableByteArray(signedContent), cms);
            var signerInfo = signedData.getSignerInfos().getSigners().iterator().next();
            assertTrue(signerInfo.verify(
                    new JcaSimpleSignerInfoVerifierBuilder().build(cert)));
        }

        var verification = new PadesSignatureVerifier().verify(
                result.encodedPdf(), null, java.util.Set.of());
        assertEquals("CAdES", verification.cades().format());
        assertEquals("B-B", verification.cades().level());
        assertArrayEquals(cert.getEncoded(), verification.signerCertificate());

        var secondKeys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var secondName = new X500Name("CN=PAdES Second");
        var secondBuilder = new JcaX509v3CertificateBuilder(
                secondName,
                BigInteger.TWO,
                Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(3600)),
                secondName,
                secondKeys.getPublic());
        secondBuilder.addExtension(
                Extension.basicConstraints, true, new BasicConstraints(false));
        secondBuilder.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        var secondCertificate = new JcaX509CertificateConverter().getCertificate(
                secondBuilder.build(
                        new JcaContentSignerBuilder("SHA256withRSA")
                                .build(secondKeys.getPrivate())));
        var secondPreparation = service.prepareSequential(
                result.encodedPdf(),
                secondCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                now.plusSeconds(1),
                "PAdES Second",
                "second approval",
                true);
        var secondSigner = Signature.getInstance("SHA256withRSA");
        secondSigner.initSign(secondKeys.getPrivate());
        secondSigner.update(secondPreparation.cmsPreparation().signedAttributes());
        var serial = service.completeBaseline(
                secondPreparation, secondSigner.sign());

        try (var signedTwice = Loader.loadPDF(serial.encodedPdf())) {
            assertEquals(2, signedTwice.getSignatureDictionaries().size());
        }
        var serialVerification = new PadesSignatureVerifier().verify(
                serial.encodedPdf(), null, java.util.Set.of());
        assertArrayEquals(
                secondCertificate.getEncoded(),
                serialVerification.signerCertificate());
    }
}
