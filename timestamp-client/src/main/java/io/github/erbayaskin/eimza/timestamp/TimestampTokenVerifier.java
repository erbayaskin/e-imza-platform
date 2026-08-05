package io.github.erbayaskin.eimza.timestamp;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Set;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tsp.TimeStampToken;

public final class TimestampTokenVerifier {

    private static final String TIMESTAMPING_EKU = "1.3.6.1.5.5.7.3.8";

    public TimestampTokenVerifier() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public TimestampResponse verify(
            byte[] encodedToken,
            String expectedDigestAlgorithmOid,
            byte[] expectedImprint,
            BigInteger expectedNonce,
            String expectedPolicyOid,
            String providerId,
            Set<TrustAnchor> trustAnchors) {
        if (trustAnchors == null || trustAnchors.isEmpty()) {
            throw new TimestampException("TSA_TRUST_NOT_CONFIGURED", "TSA güven kökü yapılandırılmamış.");
        }
        try {
            var token = new TimeStampToken(
                    org.bouncycastle.asn1.cms.ContentInfo.getInstance(encodedToken));
            var info = token.getTimeStampInfo();
            if (!expectedDigestAlgorithmOid.equals(info.getMessageImprintAlgOID().getId())
                    || !MessageDigest.isEqual(expectedImprint, info.getMessageImprintDigest())) {
                throw new TimestampException(
                        "TIMESTAMP_IMPRINT_MISMATCH",
                        "Zaman damgası messageImprint değeri beklenen özetle eşleşmiyor.");
            }
            if (expectedNonce != null && !expectedNonce.equals(info.getNonce())) {
                throw new TimestampException(
                        "TIMESTAMP_NONCE_MISMATCH",
                        "Zaman damgası nonce değeri istekle eşleşmiyor.");
            }
            if (expectedPolicyOid != null
                    && !expectedPolicyOid.isBlank()
                    && !expectedPolicyOid.equals(info.getPolicy().getId())) {
                throw new TimestampException(
                        "TIMESTAMP_POLICY_MISMATCH",
                        "Zaman damgası politika OID değeri istekle eşleşmiyor.");
            }
            var matches = token.getCertificates().getMatches(token.getSID());
            if (matches.size() != 1) {
                throw new TimestampException(
                        "TSA_SIGNER_CERTIFICATE_INVALID",
                        "TSA imzalayan sertifikası tekil olarak bulunamadı.");
            }
            var holder = (X509CertificateHolder) matches.iterator().next();
            var converter = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            var signerCertificate = converter.getCertificate(holder);
            validateTimestampingEku(signerCertificate);
            signerCertificate.checkValidity(info.getGenTime());
            token.validate(new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(holder));
            validatePath(token, signerCertificate, converter, trustAnchors);
            return new TimestampResponse(
                    token.getEncoded(),
                    info.getGenTime().toInstant(),
                    info.getPolicy().getId(),
                    info.getSerialNumber(),
                    providerId);
        } catch (TimestampException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TimestampException(
                    "TIMESTAMP_INVALID",
                    "Zaman damgası imza veya güven zinciri kontrolünden geçemedi.",
                    exception);
        }
    }

    private static void validateTimestampingEku(X509Certificate certificate) throws Exception {
        var eku = certificate.getExtendedKeyUsage();
        var critical = certificate.getCriticalExtensionOIDs();
        if (eku == null
                || eku.size() != 1
                || !eku.contains(TIMESTAMPING_EKU)
                || critical == null
                || !critical.contains("2.5.29.37")) {
            throw new TimestampException(
                    "TSA_EKU_INVALID",
                    "TSA sertifikasının kritik ve yalnız timeStamping EKU değeri olmalıdır.");
        }
    }

    private static void validatePath(
            TimeStampToken token,
            X509Certificate signerCertificate,
            JcaX509CertificateConverter converter,
            Set<TrustAnchor> trustAnchors) throws Exception {
        var certificates = new ArrayList<X509Certificate>();
        for (X509CertificateHolder holder : token.getCertificates().getMatches(null)) {
            certificates.add(converter.getCertificate(holder));
        }
        var selector = new X509CertSelector();
        selector.setCertificate(signerCertificate);
        var parameters = new PKIXBuilderParameters(trustAnchors, selector);
        parameters.setRevocationEnabled(false);
        parameters.setDate(token.getTimeStampInfo().getGenTime());
        parameters.addCertStore(CertStore.getInstance(
                "Collection", new CollectionCertStoreParameters(certificates)));
        CertPathBuilder.getInstance("PKIX").build(parameters);
    }
}
