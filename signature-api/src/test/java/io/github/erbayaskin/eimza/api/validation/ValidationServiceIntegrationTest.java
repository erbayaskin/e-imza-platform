package io.github.erbayaskin.eimza.api.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.api.truststore.CreateTrustedCertificateRequest;
import io.github.erbayaskin.eimza.api.truststore.TrustStoreService;
import io.github.erbayaskin.eimza.api.truststore.TrustedCertificateType;
import io.github.erbayaskin.eimza.api.validation.policy.UpdateValidationPolicyRequest;
import io.github.erbayaskin.eimza.api.validation.policy.ValidationPolicyConfigurationService;
import io.github.erbayaskin.eimza.api.validation.policy.ValidationPolicyMode;
import io.github.erbayaskin.eimza.core.model.ValidationIndication;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ValidationServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Autowired
    private TrustStoreService trustStoreService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private ValidationPolicyConfigurationService policyConfigurationService;

    @BeforeAll
    static void provider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void validatesAgainstDatabaseSnapshotAndReportsUnavailableRevocationAsIndeterminate()
            throws Exception {
        var chain = chain();
        var snapshot = trustStoreService.add(new CreateTrustedCertificateRequest(
                pem(chain.root()),
                TrustedCertificateType.ROOT,
                "Doğrulama test kökü",
                true));

        var report = validationService.validateCertificate(new CertificateValidationApiRequest(
                Base64.getEncoder().encodeToString(chain.leaf().getEncoded()),
                java.util.List.of(),
                snapshot.validFrom()));

        assertThat(report.mainIndication()).isEqualTo(ValidationIndication.INVALID);
        assertThat(report.cryptographicValidity()).isEqualTo(ValidationIndication.INDETERMINATE);
        assertThat(report.turkishQualifiedSignatureCompliance())
                .isEqualTo(ValidationIndication.INVALID);
        assertThat(report.trustStoreVersion()).isEqualTo(snapshot.version());
        assertThat(report.checks()).extracting(io.github.erbayaskin.eimza.validation.ValidationCheck::code)
                .contains("CERT_PATH_VALID", "REVOCATION_SERVICE_UNAVAILABLE");
    }

    @Test
    void auditOnlyNeverReturnsValidAndListsPassivePolicies() throws Exception {
        var chain = chain();
        var snapshot = trustStoreService.add(new CreateTrustedCertificateRequest(
                pem(chain.root()),
                TrustedCertificateType.ROOT,
                "Audit test kökü",
                true));
        policyConfigurationService.update(new UpdateValidationPolicyRequest(
                ValidationPolicyMode.AUDIT_ONLY, true, true, true, true, true));

        var report = validationService.validateCertificate(new CertificateValidationApiRequest(
                Base64.getEncoder().encodeToString(chain.leaf().getEncoded()),
                java.util.List.of(),
                snapshot.validFrom()));

        assertThat(report.policyMode()).isEqualTo(ValidationPolicyMode.AUDIT_ONLY);
        assertThat(report.mainIndication()).isNotEqualTo(ValidationIndication.VALID);
        assertThat(report.passivePolicies()).containsExactlyInAnyOrder(
                "QC_COMPLIANCE",
                "CERTIFICATE_POLICY",
                "REVOCATION_AVAILABILITY",
                "SIGNATURE_POLICY");
        assertThat(report.checks()).extracting(io.github.erbayaskin.eimza.validation.ValidationCheck::code)
                .contains("POLICY_PASSIVE");
    }

    @Test
    void malformedSignatureReturnsInvalidReportWhenTrustStoreIsEmpty() {
        var report = validationService.validateSignature(new SignatureValidationApiRequest(
                Base64.getEncoder().encodeToString("document".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}),
                null,
                "CADES"));

        assertThat(report.mainIndication()).isEqualTo(ValidationIndication.INVALID);
        assertThat(report.checks()).extracting(io.github.erbayaskin.eimza.validation.ValidationCheck::code)
                .contains("CADES_INVALID");
    }

    private static TestChain chain() throws Exception {
        var rootKeys = keys();
        var root = certificate("CN=API Root", rootKeys, null, rootKeys, true, BigInteger.valueOf(100));
        var leafKeys = keys();
        var leaf = certificate("CN=API Signer", leafKeys, root, rootKeys, false, BigInteger.valueOf(101));
        return new TestChain(root, leaf);
    }

    private static X509Certificate certificate(
            String subject,
            KeyPair subjectKeys,
            X509Certificate issuerCertificate,
            KeyPair issuerKeys,
            boolean ca,
            BigInteger serial) throws Exception {
        var issuer = issuerCertificate == null
                ? new X500Name(subject)
                : X500Name.getInstance(issuerCertificate.getSubjectX500Principal().getEncoded());
        var builder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                Date.from(NOW.minusSeconds(3600)),
                Date.from(NOW.plusSeconds(86400L * 365)),
                new X500Name(subject),
                subjectKeys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(ca
                        ? KeyUsage.keyCertSign | KeyUsage.cRLSign
                        : KeyUsage.digitalSignature));
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

    private static String pem(X509Certificate certificate) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----";
    }

    private record TestChain(X509Certificate root, X509Certificate leaf) {}
}
