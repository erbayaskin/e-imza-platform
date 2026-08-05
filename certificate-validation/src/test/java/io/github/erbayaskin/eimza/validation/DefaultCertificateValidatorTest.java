package io.github.erbayaskin.eimza.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.core.model.ValidationIndication;

class DefaultCertificateValidatorTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");
    private static final String CERTIFICATE_POLICY = "1.2.3.4.100";

    @BeforeAll
    static void provider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void validatesPathQualificationPolicyAndGoodRevocation() throws Exception {
        var chain = chain();
        var validator = validator(chain.root(), RevocationStatus.GOOD);

        var result = validator.validate(request(chain.leaf()));

        assertThat(result.cryptographicValidity()).isEqualTo(ValidationIndication.VALID);
        assertThat(result.turkishQualification()).isEqualTo(ValidationIndication.VALID);
        assertThat(result.trustStoreVersion()).isEqualTo("test-v1");
        assertThat(result.checks()).extracting(ValidationCheck::code)
                .contains("CERT_PATH_VALID", "QC_COMPLIANCE", "CERTIFICATE_POLICY", "CERT_STATUS_GOOD");
    }

    @Test
    void revokedCertificateIsInvalid() throws Exception {
        var chain = chain();
        var result = validator(chain.root(), RevocationStatus.REVOKED)
                .validate(request(chain.leaf()));

        assertThat(result.cryptographicValidity()).isEqualTo(ValidationIndication.INVALID);
        assertThat(result.checks()).anyMatch(check -> check.code().equals("CERT_REVOKED"));
    }

    @Test
    void unavailableRequiredRevocationIsIndeterminateNotInvalid() throws Exception {
        var chain = chain();
        var result = validator(chain.root(), RevocationStatus.UNAVAILABLE)
                .validate(request(chain.leaf()));

        assertThat(result.cryptographicValidity()).isEqualTo(ValidationIndication.INDETERMINATE);
        assertThat(result.checks()).anyMatch(
                check -> check.code().equals("REVOCATION_SERVICE_UNAVAILABLE"));
    }

    @Test
    void validatesEmbeddedCrlBeforeUnavailableNetworkFallback() throws Exception {
        var chain = chain();
        var crlBuilder = new X509v2CRLBuilder(
                X500Name.getInstance(chain.root().getSubjectX500Principal().getEncoded()),
                Date.from(NOW.minusSeconds(60)));
        crlBuilder.setNextUpdate(Date.from(NOW.plusSeconds(3600)));
        var crlSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(chain.rootKeys().getPrivate());
        var embedded = new EmbeddedRevocationValue(
                "CRL", crlBuilder.build(crlSigner).getEncoded());
        var evaluator = new NetworkRevocationDataProvider(
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                Duration.ofSeconds(1),
                Duration.ofHours(24),
                Duration.ofMinutes(5));
        RevocationDataProvider unavailable = (certificate, issuer, validationTime) ->
                new RevocationEvidence(
                        RevocationStatus.UNAVAILABLE,
                        "NONE",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new byte[0]);
        TrustedCertificateProvider trusted = validationTime ->
                new TrustedCertificateProvider.TrustedCertificateSnapshot(
                        "test-v1",
                        NOW.minusSeconds(3600),
                        null,
                        List.of(new TrustedCertificateProvider.TrustedCertificate(
                                chain.root(),
                                TrustedCertificateProvider.TrustedCertificateType.ROOT,
                                "ROOT")));
        var validator = new DefaultCertificateValidator(
                trusted,
                new EmbeddedFirstRevocationDataProvider(
                        List.of(embedded), evaluator, unavailable));

        var result = validator.validate(request(chain.leaf()));

        assertThat(result.cryptographicValidity()).isEqualTo(ValidationIndication.VALID);
        assertThat(result.checks()).anyMatch(check ->
                check.code().equals("CERT_STATUS_GOOD")
                        && "CRL".equals(check.details().get("sourceType")));
    }

    private static DefaultCertificateValidator validator(
            X509Certificate root,
            RevocationStatus status) {
        TrustedCertificateProvider trusted = validationTime ->
                new TrustedCertificateProvider.TrustedCertificateSnapshot(
                        "test-v1",
                        NOW.minusSeconds(3600),
                        null,
                        List.of(new TrustedCertificateProvider.TrustedCertificate(
                                root,
                                TrustedCertificateProvider.TrustedCertificateType.ROOT,
                                "ROOT")));
        RevocationDataProvider revocation = (certificate, issuer, validationTime) ->
                new RevocationEvidence(
                        status,
                        status == RevocationStatus.UNAVAILABLE ? "NONE" : "OCSP",
                        NOW,
                        NOW,
                        NOW.plusSeconds(3600),
                        status == RevocationStatus.REVOKED ? NOW.minusSeconds(60) : null,
                        "https://ocsp.example.invalid",
                        status == RevocationStatus.UNAVAILABLE ? new byte[0] : new byte[] {1, 2, 3});
        return new DefaultCertificateValidator(trusted, revocation);
    }

    private static CertificateValidationRequest request(X509Certificate leaf) throws Exception {
        var policy = new CertificateValidationPolicy(
                2048,
                256,
                true,
                true,
                Set.of(CERTIFICATE_POLICY),
                true,
                Duration.ofHours(24),
                Duration.ofMinutes(5));
        return new CertificateValidationRequest(
                leaf.getEncoded(), List.of(), NOW, "test-policy-v1", policy);
    }

    private static TestChain chain() throws Exception {
        var rootKeys = keys();
        var root = certificate(
                "CN=Root",
                rootKeys,
                null,
                rootKeys,
                true,
                false,
                BigInteger.ONE);
        var leafKeys = keys();
        var leaf = certificate(
                "CN=Qualified Signer",
                leafKeys,
                root,
                rootKeys,
                false,
                true,
                BigInteger.TWO);
        return new TestChain(root, leaf, rootKeys);
    }

    private static X509Certificate certificate(
            String subject,
            KeyPair subjectKeys,
            X509Certificate issuerCertificate,
            KeyPair issuerKeys,
            boolean ca,
            boolean qualified,
            BigInteger serial) throws Exception {
        var issuer = issuerCertificate == null
                ? new X500Name(subject)
                : X500Name.getInstance(issuerCertificate.getSubjectX500Principal().getEncoded());
        var builder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                Date.from(NOW.minusSeconds(3600)),
                Date.from(NOW.plusSeconds(86400)),
                new X500Name(subject),
                subjectKeys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(ca
                        ? KeyUsage.keyCertSign | KeyUsage.cRLSign
                        : KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        if (qualified) {
            var statement = new ASN1EncodableVector();
            statement.add(new ASN1ObjectIdentifier("0.4.0.1862.1.1"));
            builder.addExtension(
                    new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3"),
                    false,
                    new DERSequence(new DERSequence(statement)));
            builder.addExtension(
                    Extension.certificatePolicies,
                    false,
                    new CertificatePolicies(
                            new PolicyInformation(new ASN1ObjectIdentifier(CERTIFICATE_POLICY))));
        }
        var signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(issuerKeys.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    private static KeyPair keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private record TestChain(
            X509Certificate root,
            X509Certificate leaf,
            KeyPair rootKeys) {}
}
