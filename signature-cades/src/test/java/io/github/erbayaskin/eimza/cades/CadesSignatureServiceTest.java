package io.github.erbayaskin.eimza.cades;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.timestamp.TimestampClient;
import io.github.erbayaskin.eimza.timestamp.TimestampRequest;
import io.github.erbayaskin.eimza.timestamp.TimestampResponse;

class CadesSignatureServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");
    private static final String TSA_POLICY = "1.2.3.4.10";

    @BeforeAll
    static void installProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void createsAndIndependentlyVerifiesDetachedCadesBaselineT() throws Exception {
        var signerKeys = rsaKeys();
        var signerCertificate = certificate(
                "CN=Test Signer", signerKeys, null, signerKeys, false, false, BigInteger.TEN);
        var authority = timestampAuthority();
        var content = "Faz 5 CAdES B-T örnek içeriği".getBytes(StandardCharsets.UTF_8);
        var policyDigest = new byte[32];
        policyDigest[0] = 7;
        var policy = new SignaturePolicy(
                "1.2.3.4.20",
                "2.16.840.1.101.3.4.2.1",
                policyDigest,
                "https://example.invalid/policy");
        var service = new CadesSignatureService();

        var preparation = service.prepare(
                content,
                signerCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                policy,
                NOW);
        var cardSignature = sign(preparation.signedAttributes(), signerKeys);
        var result = service.completeWithTimestamp(
                preparation, cardSignature, authority.client(), TSA_POLICY);

        var verification = new CadesSignatureVerifier().verifyDetached(
                content,
                result.encodedSignature(),
                policy,
                Set.of(new TrustAnchor(authority.rootCertificate(), null)));

        assertThat(verification.cryptographicValidity()).isTrue();
        assertThat(verification.level()).isEqualTo("B-T");
        assertThat(verification.signaturePolicyOid()).isEqualTo(policy.oid());
        assertThat(verification.timestampPolicyOid()).isEqualTo(TSA_POLICY);
        assertThat(verification.revocationStatus()).isEqualTo("NOT_EMBEDDED");
    }

    @Test
    void rejectsContentChangedAfterSigning() throws Exception {
        var fixture = signedFixture();
        var changed = "değiştirilmiş içerik".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new CadesSignatureVerifier().verifyDetached(
                        changed,
                        fixture.result().encodedSignature(),
                        null,
                        Set.of(new TrustAnchor(fixture.authority().rootCertificate(), null))))
                .isInstanceOf(CadesException.class);
    }

    @Test
    void createsAndVerifiesAttachedCadesWithSha384() throws Exception {
        var signerKeys = rsaKeys();
        var signerCertificate = certificate(
                "CN=Attached Signer", signerKeys, null, signerKeys, false, false, BigInteger.valueOf(30));
        var content = "attached CAdES içeriği".getBytes(StandardCharsets.UTF_8);
        var service = new CadesSignatureService();
        var preparation = service.prepare(
                content,
                signerCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA384,
                null,
                NOW,
                true);
        var signer = Signature.getInstance("SHA384withRSA");
        signer.initSign(signerKeys.getPrivate());
        signer.update(preparation.signedAttributes());
        var result = service.completeBaseline(preparation, signer.sign());

        var verification = new CadesSignatureVerifier().verify(
                null, result.encodedSignature(), null, Set.of());

        assertThat(verification.cryptographicValidity()).isTrue();
        assertThat(verification.level()).isEqualTo("B-B");
    }

    @Test
    void reportsExpiredSignerCertificateWithSpecificCode() throws Exception {
        var signerKeys = rsaKeys();
        var signerCertificate = certificate(
                "CN=Expired Signer",
                signerKeys,
                null,
                signerKeys,
                false,
                false,
                BigInteger.valueOf(31));

        assertThatThrownBy(() -> new CadesSignatureService().prepare(
                        "content".getBytes(StandardCharsets.UTF_8),
                        signerCertificate,
                        CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                        null,
                        NOW.plusSeconds(86400L * 3651)))
                .isInstanceOfSatisfying(
                        CadesException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("SIGNER_CERTIFICATE_EXPIRED"));
    }

    @Test
    void permitsExpiredCertificateOnlyWhenValidityPolicyIsExplicitlyPassive() throws Exception {
        var signerKeys = rsaKeys();
        var signerCertificate = certificate(
                "CN=Expired Physical Test",
                signerKeys,
                null,
                signerKeys,
                false,
                false,
                BigInteger.valueOf(32));

        var preparation = new CadesSignatureService().prepare(
                "physical-test".getBytes(StandardCharsets.UTF_8),
                signerCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW.plusSeconds(86400L * 3651),
                false,
                false);

        assertThat(preparation.signerCertificate()).isEqualTo(signerCertificate.getEncoded());
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(signerKeys.getPrivate());
        signer.update(preparation.signedAttributes());
        var result = new CadesSignatureService().completeBaseline(
                preparation,
                signer.sign());

        assertThatThrownBy(() -> new CadesSignatureVerifier().verify(
                        "physical-test".getBytes(StandardCharsets.UTF_8),
                        result.encodedSignature(),
                        null,
                        Set.of()))
                .isInstanceOfSatisfying(
                        CadesException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("SIGNER_CERTIFICATE_EXPIRED"));
        assertThat(new CadesSignatureVerifier().verify(
                        "physical-test".getBytes(StandardCharsets.UTF_8),
                        result.encodedSignature(),
                        null,
                        Set.of(),
                        false)
                .cryptographicValidity()).isTrue();
    }

    @Test
    void rejectsCardSignatureThatDoesNotCoverPreparedAttributes() throws Exception {
        var signerKeys = rsaKeys();
        var signerCertificate = certificate(
                "CN=Test Signer", signerKeys, null, signerKeys, false, false, BigInteger.TEN);
        var preparation = new CadesSignatureService().prepare(
                new byte[] {1, 2, 3},
                signerCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW);
        var invalidSignature = sign(new byte[] {9, 9, 9}, signerKeys);

        assertThatThrownBy(() -> new CadesSignatureService().completeWithTimestamp(
                        preparation, invalidSignature, timestampAuthority().client(), TSA_POLICY))
                .isInstanceOfSatisfying(
                        CadesException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CARD_SIGNATURE_INVALID"));
    }

    @Test
    void rejectsUnexpectedSignaturePolicy() throws Exception {
        var fixture = signedFixture();
        var unexpected = new SignaturePolicy(
                "1.2.3.4.999",
                "2.16.840.1.101.3.4.2.1",
                new byte[32],
                null);

        assertThatThrownBy(() -> new CadesSignatureVerifier().verifyDetached(
                        fixture.content(),
                        fixture.result().encodedSignature(),
                        unexpected,
                        Set.of(new TrustAnchor(fixture.authority().rootCertificate(), null))))
                .isInstanceOfSatisfying(
                        CadesException.class,
                        exception -> assertThat(exception.code()).isEqualTo("POLICY_NOT_FOUND"));
    }

    @Test
    void augmentsBaselineTToBaselineLTAndVerifiesEmbeddedMaterial() throws Exception {
        var fixture = signedFixture();
        var crl = crl(fixture.signerCertificate(), fixture.signerKeys());
        var material = new CadesValidationMaterial(
                List.of(
                        fixture.signerCertificate().getEncoded(),
                        fixture.authority().tsaCertificate().getEncoded(),
                        fixture.authority().rootCertificate().getEncoded()),
                List.of(new CadesRevocationValue(CadesRevocationType.CRL, crl)));

        var result = new CadesLongTermService().augmentToBaselineLT(
                fixture.result().encodedSignature(), material);
        var verification = new CadesSignatureVerifier().verifyDetached(
                fixture.content(),
                result.encodedSignature(),
                null,
                Set.of(new TrustAnchor(fixture.authority().rootCertificate(), null)));

        assertThat(result.level()).isEqualTo("B-LT");
        assertThat(result.revocationValueCount()).isEqualTo(1);
        assertThat(verification.level()).isEqualTo("B-LT");
        assertThat(verification.embeddedCertificateCount()).isGreaterThanOrEqualTo(3);
        assertThat(verification.embeddedRevocationValueCount()).isEqualTo(1);
    }

    @Test
    void createsAndRenewsBaselineLTAArchiveTimestampV3() throws Exception {
        var fixture = signedFixture();
        var material = new CadesValidationMaterial(
                List.of(
                        fixture.signerCertificate().getEncoded(),
                        fixture.authority().tsaCertificate().getEncoded(),
                        fixture.authority().rootCertificate().getEncoded()),
                List.of(new CadesRevocationValue(
                        CadesRevocationType.CRL,
                        crl(fixture.signerCertificate(), fixture.signerKeys()))));
        var service = new CadesLongTermService();
        var baselineLT = service.augmentToBaselineLT(
                fixture.result().encodedSignature(), material);

        var baselineLTA = service.augmentToBaselineLTA(
                fixture.content(), baselineLT.encodedSignature(), fixture.authority().client(), TSA_POLICY);
        var renewed = service.renewBaselineLTA(
                fixture.content(), baselineLTA.encodedSignature(), fixture.authority().client(), TSA_POLICY);
        var verification = new CadesSignatureVerifier().verifyDetached(
                fixture.content(),
                renewed.encodedSignature(),
                null,
                Set.of(new TrustAnchor(fixture.authority().rootCertificate(), null)));

        assertThat(baselineLTA.archiveTimestampCount()).isEqualTo(1);
        assertThat(renewed.archiveTimestampCount()).isEqualTo(2);
        assertThat(verification.level()).isEqualTo("B-LTA");
        assertThat(verification.archiveTimestampGenerationTimes()).hasSize(2);
    }

    @Test
    void rejectsBaselineLTWithoutRevocationEvidence() throws Exception {
        var fixture = signedFixture();

        assertThatThrownBy(() -> new CadesLongTermService().augmentToBaselineLT(
                        fixture.result().encodedSignature(),
                        new CadesValidationMaterial(
                                List.of(fixture.signerCertificate().getEncoded()),
                                List.of())))
                .isInstanceOfSatisfying(
                        CadesException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("CADES_LT_REVOCATION_DATA_MISSING"));
    }

    @Test
    void extractsEmbeddedContentOnlyFromAttachedCades() throws Exception {
        var content = "attached-content".getBytes(StandardCharsets.UTF_8);
        var keys = rsaKeys();
        var certificate = certificate(
                "CN=Attached Signer",
                keys,
                null,
                keys,
                false,
                false,
                BigInteger.valueOf(40));
        var counterKeys = rsaKeys();
        var counterCertificate = certificate(
                "CN=Attached Counter Signer",
                counterKeys,
                null,
                counterKeys,
                false,
                false,
                BigInteger.valueOf(44));
        var service = new CadesSignatureService();
        var attachedPreparation = service.prepare(
                content,
                certificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW,
                true,
                true);
        var attached = service.completeBaseline(
                attachedPreparation,
                sign(attachedPreparation.signedAttributes(), keys));
        var detachedPreparation = service.prepare(
                content,
                certificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW,
                false,
                true);
        var detached = service.completeBaseline(
                detachedPreparation,
                sign(detachedPreparation.signedAttributes(), keys));
        var counterPreparation = service.prepareCounterSignature(
                attached.encodedSignature(),
                0,
                counterCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW.plusSeconds(1),
                true);
        var serialAttached = service.completeBaseline(
                counterPreparation,
                sign(counterPreparation.signedAttributes(), counterKeys));

        assertThat(new CadesSignatureVerifier()
                        .verify(null, serialAttached.encodedSignature(), null, Set.of())
                        .cryptographicValidity())
                .isTrue();

        assertThat(service.extractAttachedContent(attached.encodedSignature()))
                .isEqualTo(content);
        assertThat(service.extractAttachedContent(serialAttached.encodedSignature()))
                .isEqualTo(content);
        assertThatThrownBy(() -> service.extractAttachedContent(detached.encodedSignature()))
                .isInstanceOfSatisfying(
                        CadesException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("CADES_ATTACHED_CONTENT_MISSING"));
    }

    @Test
    void createsParallelAndSerialCadesSignatures() throws Exception {
        var content = "multi-cades".getBytes(StandardCharsets.UTF_8);
        var firstKeys = rsaKeys();
        var firstCertificate = certificate(
                "CN=First Signer",
                firstKeys,
                null,
                firstKeys,
                false,
                false,
                BigInteger.valueOf(41));
        var secondKeys = rsaKeys();
        var secondCertificate = certificate(
                "CN=Second Signer",
                secondKeys,
                null,
                secondKeys,
                false,
                false,
                BigInteger.valueOf(42));
        var counterKeys = rsaKeys();
        var counterCertificate = certificate(
                "CN=Counter Signer",
                counterKeys,
                null,
                counterKeys,
                false,
                false,
                BigInteger.valueOf(43));
        var service = new CadesSignatureService();

        var firstPreparation = service.prepare(
                content,
                firstCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW);
        var first = service.completeBaseline(
                firstPreparation,
                sign(firstPreparation.signedAttributes(), firstKeys));
        service.validateDetachedContent(first.encodedSignature(), content);
        assertThatThrownBy(() -> service.validateDetachedContent(
                        first.encodedSignature(),
                        "wrong-content".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(
                        CadesException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("CADES_DETACHED_CONTENT_MISMATCH"));

        var parallelPreparation = service.prepareParallel(
                first.encodedSignature(),
                content,
                secondCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW.plusSeconds(1),
                false,
                true);
        var parallel = service.completeBaseline(
                parallelPreparation,
                sign(parallelPreparation.signedAttributes(), secondKeys));
        var parallelCms = new org.bouncycastle.cms.CMSSignedData(
                new CMSProcessableByteArray(content),
                parallel.encodedSignature());
        assertThat(parallelCms.getSignerInfos().size()).isEqualTo(2);
        assertThat(new CadesSignatureVerifier().verifyDetached(
                        content, parallel.encodedSignature(), null, Set.of())
                .cryptographicValidity())
                .isTrue();

        var serialPreparation = service.prepareCounterSignature(
                parallel.encodedSignature(),
                0,
                counterCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW.plusSeconds(2),
                true);
        var serial = service.completeBaseline(
                serialPreparation,
                sign(serialPreparation.signedAttributes(), counterKeys));
        var serialCms = new org.bouncycastle.cms.CMSSignedData(
                new CMSProcessableByteArray(content),
                serial.encodedSignature());
        assertThat(serialCms.getSignerInfos().size()).isEqualTo(2);
        assertThat(serialCms.getSignerInfos().getSigners().stream()
                        .mapToInt(item -> item.getCounterSignatures().size())
                        .sum())
                .isEqualTo(1);
        assertThat(new CadesSignatureVerifier().verifyDetached(
                        content, serial.encodedSignature(), null, Set.of())
                .cryptographicValidity())
                .isTrue();
    }

    private static SignedFixture signedFixture() throws Exception {
        var signerKeys = rsaKeys();
        var signerCertificate = certificate(
                "CN=Test Signer", signerKeys, null, signerKeys, false, false, BigInteger.TEN);
        var authority = timestampAuthority();
        var content = "özgün içerik".getBytes(StandardCharsets.UTF_8);
        var service = new CadesSignatureService();
        var preparation = service.prepare(
                content,
                signerCertificate,
                CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                null,
                NOW);
        var result = service.completeWithTimestamp(
                preparation, sign(preparation.signedAttributes(), signerKeys), authority.client(), TSA_POLICY);
        return new SignedFixture(
                content, result, authority, signerCertificate, signerKeys);
    }

    private static byte[] crl(
            X509Certificate issuerCertificate,
            KeyPair issuerKeys) throws Exception {
        var builder = new X509v2CRLBuilder(
                X500Name.getInstance(issuerCertificate.getSubjectX500Principal().getEncoded()),
                Date.from(NOW.minusSeconds(60)));
        builder.setNextUpdate(Date.from(NOW.plusSeconds(3600)));
        var signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(issuerKeys.getPrivate());
        return builder.build(signer).getEncoded();
    }

    private static byte[] sign(byte[] signedAttributes, KeyPair keys) throws Exception {
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update(signedAttributes);
        return signature.sign();
    }

    private static TestAuthority timestampAuthority() throws Exception {
        var rootKeys = rsaKeys();
        var root = certificate(
                "CN=Test Root", rootKeys, null, rootKeys, true, false, BigInteger.ONE);
        var tsaKeys = rsaKeys();
        var tsa = certificate(
                "CN=Test TSA", tsaKeys, root, rootKeys, false, true, BigInteger.TWO);
        return new TestAuthority(root, tsa, tsaKeys);
    }

    private static X509Certificate certificate(
            String subject,
            KeyPair subjectKeys,
            X509Certificate issuerCertificate,
            KeyPair issuerKeys,
            boolean ca,
            boolean timestamping,
            BigInteger serial) throws Exception {
        var issuer = issuerCertificate == null
                ? new X500Name(subject)
                : X500Name.getInstance(issuerCertificate.getSubjectX500Principal().getEncoded());
        var builder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                Date.from(NOW.minusSeconds(3600)),
                Date.from(NOW.plusSeconds(86400L * 3650)),
                new X500Name(subject),
                subjectKeys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(ca
                        ? KeyUsage.keyCertSign | KeyUsage.cRLSign
                        : KeyUsage.digitalSignature));
        if (timestamping) {
            builder.addExtension(
                    Extension.extendedKeyUsage,
                    true,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        }
        var signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(issuerKeys.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    private static KeyPair rsaKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private record SignedFixture(
            byte[] content,
            CadesSignatureResult result,
            TestAuthority authority,
            X509Certificate signerCertificate,
            KeyPair signerKeys) {}

    private record TestAuthority(
            X509Certificate rootCertificate,
            X509Certificate tsaCertificate,
            KeyPair tsaKeys) {

        TimestampClient client() {
            return request -> {
                try {
                    var generator = new TimeStampRequestGenerator();
                    generator.setCertReq(true);
                    generator.setReqPolicy(TSA_POLICY);
                    var bcRequest = generator.generate(
                            new ASN1ObjectIdentifier(request.digestAlgorithmOid()),
                            request.messageImprint(),
                            request.nonce());
                    var signerInfo = new JcaSimpleSignerInfoGeneratorBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build("SHA256withRSA", tsaKeys.getPrivate(), tsaCertificate);
                    var digestCalculator = new JcaDigestCalculatorProviderBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build()
                            .get(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                    NISTObjectIdentifiers.id_sha256));
                    var tokenGenerator = new TimeStampTokenGenerator(
                            signerInfo,
                            digestCalculator,
                            new ASN1ObjectIdentifier(TSA_POLICY));
                    tokenGenerator.addCertificates(
                            new JcaCertStore(List.of(tsaCertificate, rootCertificate)));
                    var token = tokenGenerator.generate(
                            bcRequest,
                            BigInteger.valueOf(100),
                            Date.from(NOW.plusSeconds(5)));
                    return new TimestampResponse(
                            token.getEncoded(),
                            NOW.plusSeconds(5),
                            TSA_POLICY,
                            BigInteger.valueOf(100),
                            "test-tsa");
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            };
        }
    }
}
