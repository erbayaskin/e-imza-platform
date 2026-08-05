package io.github.erbayaskin.eimza.timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
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
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TimestampResponseProcessorTest {

    private static final String SHA256_OID = "2.16.840.1.101.3.4.2.1";
    private static final String POLICY_OID = "1.2.3.4.5.6";

    @BeforeAll
    static void installProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void validatesRfc3161BindingSignatureEkuAndTrustPath() throws Exception {
        var authority = authority();
        var request = new TimestampRequest(
                SHA256_OID, new byte[32], BigInteger.valueOf(42), POLICY_OID);
        var encodedResponse = authority.response(request);

        var result = new TimestampResponseProcessor().process(
                request,
                encodedResponse,
                "test-tsa",
                Set.of(new TrustAnchor(authority.rootCertificate(), null)));

        assertThat(result.policyOid()).isEqualTo(POLICY_OID);
        assertThat(result.serialNumber()).isEqualTo(BigInteger.valueOf(99));
        assertThat(result.encodedToken()).isNotEmpty();
    }

    @Test
    void rejectsResponseCreatedForDifferentImprint() throws Exception {
        var authority = authority();
        var original = new TimestampRequest(
                SHA256_OID, new byte[32], BigInteger.valueOf(42), POLICY_OID);
        var encodedResponse = authority.response(original);
        var different = new byte[32];
        different[0] = 1;
        var changed = new TimestampRequest(
                SHA256_OID, different, BigInteger.valueOf(42), POLICY_OID);

        assertThatThrownBy(() -> new TimestampResponseProcessor().process(
                        changed,
                        encodedResponse,
                        "test-tsa",
                        Set.of(new TrustAnchor(authority.rootCertificate(), null))))
                .isInstanceOfSatisfying(
                        TimestampException.class,
                        exception -> assertThat(exception.code()).isEqualTo("TIMESTAMP_INVALID"));
    }

    private static TestAuthority authority() throws Exception {
        var rootKeys = rsaKeys();
        var root = certificate(
                "CN=Test Root",
                rootKeys,
                null,
                rootKeys,
                true,
                false,
                BigInteger.ONE);
        var tsaKeys = rsaKeys();
        var tsa = certificate(
                "CN=Test TSA",
                tsaKeys,
                root,
                rootKeys,
                false,
                true,
                BigInteger.TWO);
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
        var now = Instant.parse("2026-07-28T12:00:00Z");
        var issuer = issuerCertificate == null
                ? new X500Name(subject)
                : X500Name.getInstance(issuerCertificate.getSubjectX500Principal().getEncoded());
        var builder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                Date.from(now.minusSeconds(3600)),
                Date.from(now.plusSeconds(86400L * 3650)),
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

    private record TestAuthority(
            X509Certificate rootCertificate,
            X509Certificate tsaCertificate,
            KeyPair tsaKeys) {

        byte[] response(TimestampRequest request) throws Exception {
            var requestGenerator = new TimeStampRequestGenerator();
            requestGenerator.setCertReq(true);
            requestGenerator.setReqPolicy(POLICY_OID);
            var bcRequest = requestGenerator.generate(
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
                    new ASN1ObjectIdentifier(POLICY_OID));
            tokenGenerator.addCertificates(new JcaCertStore(List.of(tsaCertificate, rootCertificate)));
            var responseGenerator = new TimeStampResponseGenerator(
                    tokenGenerator, Set.of(SHA256_OID), Set.of(POLICY_OID));
            return responseGenerator
                    .generateGrantedResponse(
                            bcRequest,
                            BigInteger.valueOf(99),
                            Date.from(Instant.parse("2026-07-28T12:00:05Z")))
                    .getEncoded();
        }
    }
}
