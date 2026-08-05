package io.github.erbayaskin.eimza.cades;

import java.security.MessageDigest;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.TrustAnchor;
import java.util.Set;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.esf.SignaturePolicyIdentifier;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import io.github.erbayaskin.eimza.timestamp.TimestampTokenVerifier;
import io.github.erbayaskin.eimza.timestamp.TimestampException;

public class CadesSignatureVerifier {

    private final TimestampTokenVerifier timestampVerifier = new TimestampTokenVerifier();
    private final CadesLongTermVerifier longTermVerifier = new CadesLongTermVerifier();

    public CadesVerificationResult verifyDetached(
            byte[] content,
            byte[] encodedSignature,
            SignaturePolicy expectedSignaturePolicy,
            Set<TrustAnchor> tsaTrustAnchors) {
        if (content == null) {
            throw new CadesException(
                    "DETACHED_CONTENT_REQUIRED",
                    "Ayrık CAdES doğrulaması için orijinal içerik zorunludur.");
        }
        return verify(content, encodedSignature, expectedSignaturePolicy, tsaTrustAnchors);
    }

    public CadesVerificationResult verify(
            byte[] content,
            byte[] encodedSignature,
            SignaturePolicy expectedSignaturePolicy,
            Set<TrustAnchor> tsaTrustAnchors) {
        return verify(
                content,
                encodedSignature,
                expectedSignaturePolicy,
                tsaTrustAnchors,
                true);
    }

    public CadesVerificationResult verify(
            byte[] content,
            byte[] encodedSignature,
            SignaturePolicy expectedSignaturePolicy,
            Set<TrustAnchor> tsaTrustAnchors,
            boolean certificateValidityCheckActive) {
        try {
            var embedded = new CMSSignedData(encodedSignature);
            var attached = embedded.getSignedContent() != null;
            if (!attached && content == null) {
                throw new CadesException(
                        "DETACHED_CONTENT_REQUIRED",
                        "Ayrık CAdES doğrulaması için orijinal içerik zorunludur.");
            }
            var effectiveContent = attached
                    ? (byte[]) embedded.getSignedContent().getContent()
                    : content;
            var signedData = attached
                    ? embedded
                    : new CMSSignedData(new CMSProcessableByteArray(effectiveContent), encodedSignature);
            var signers = signedData.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                throw new CadesException(
                        "SIGNER_COUNT_INVALID",
                        "CAdES en az bir imzalayan içermelidir.");
            }
            for (var candidate : signers) {
                verifySigner(
                        candidate,
                        signedData,
                        expectedSignaturePolicy,
                        certificateValidityCheckActive);
            }
            var signer = signers.iterator().next();
            var certificateMatches = signedData.getCertificates().getMatches(signer.getSID());
            var certificateHolder = (X509CertificateHolder) certificateMatches.iterator().next();
            var policyOid = verifyPolicy(signer.getSignedAttributes(), expectedSignaturePolicy);
            var timestampAttribute = signer.getUnsignedAttributes() == null
                    ? null
                    : signer.getUnsignedAttributes().get(
                            PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
            if (timestampAttribute == null) {
                return new CadesVerificationResult(
                        true,
                        "CAdES",
                        "B-B",
                        policyOid,
                        null,
                        null,
                        "NOT_EMBEDDED",
                        signedData.getCertificates().getMatches(null).size(),
                        0,
                        java.util.List.of());
            }
            if (timestampAttribute.getAttrValues().size() != 1) {
                throw new CadesException(
                        "TIMESTAMP_INVALID",
                        "CAdES signature-time-stamp özelliği tekil olmalıdır.");
            }
            var tokenContent = ContentInfo.getInstance(
                    timestampAttribute.getAttrValues().getObjectAt(0));
            var signatureImprint = MessageDigest.getInstance("SHA-256")
                    .digest(signer.getSignature());
            var timestamp = timestampVerifier.verify(
                    tokenContent.getEncoded(),
                    "2.16.840.1.101.3.4.2.1",
                    signatureImprint,
                    null,
                    null,
                    "embedded",
                    tsaTrustAnchors);
            var longTerm = longTermVerifier.verify(
                    effectiveContent, encodedSignature, tsaTrustAnchors);
            return new CadesVerificationResult(
                    true,
                    "CAdES",
                    longTerm.level(),
                    policyOid,
                    timestamp.generationTime(),
                    timestamp.policyOid(),
                    longTerm.embeddedRevocationValueCount() == 0
                            ? "NOT_EMBEDDED"
                            : "EMBEDDED",
                    longTerm.embeddedCertificateCount(),
                    longTerm.embeddedRevocationValueCount(),
                    longTerm.archiveTimestampGenerationTimes());
        } catch (TimestampException exception) {
            throw new CadesException(exception.code(), exception.getMessage(), exception);
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException(
                    "CADES_INVALID",
                    "CAdES imzası doğrulanamadı.",
                    exception);
        }
    }

    private static void verifySigner(
            org.bouncycastle.cms.SignerInformation signer,
            CMSSignedData signedData,
            SignaturePolicy expectedSignaturePolicy,
            boolean certificateValidityCheckActive) throws Exception {
        var matches = signedData.getCertificates().getMatches(signer.getSID());
        if (matches.size() != 1) {
            throw new CadesException(
                    "SIGNER_CERTIFICATE_INVALID",
                    "İmzalayan sertifikası tekil olarak bulunamadı.");
        }
        var holder = (X509CertificateHolder) matches.iterator().next();
        var certificate = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
        if (certificateValidityCheckActive) {
            validateCertificateAtSigningTime(signer, certificate);
        }
        var verifierBuilder = new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME);
        var verifier = certificateValidityCheckActive
                ? verifierBuilder.build(holder)
                : verifierBuilder.build(certificate.getPublicKey());
        if (!signer.verify(verifier)) {
            throw new CadesException(
                    "CRYPTO_FAILURE",
                    "CAdES imzalayan veya karşı-imza değeri geçersiz.");
        }
        verifySigningCertificateV2(signer.getSignedAttributes(), holder);
        verifyPolicy(signer.getSignedAttributes(), expectedSignaturePolicy);
        for (var counterSigner : signer.getCounterSignatures().getSigners()) {
            verifySigner(
                    counterSigner,
                    signedData,
                    expectedSignaturePolicy,
                    certificateValidityCheckActive);
        }
    }

    private static void verifySigningCertificateV2(
            org.bouncycastle.asn1.cms.AttributeTable attributes,
            X509CertificateHolder certificate) throws Exception {
        var attribute = attributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2);
        if (attribute == null || attribute.getAttrValues().size() != 1) {
            throw new CadesException(
                    "SIGNING_CERTIFICATE_V2_MISSING",
                    "signing-certificate-v2 imzalı özelliği bulunamadı.");
        }
        var signingCertificate = SigningCertificateV2.getInstance(
                attribute.getAttrValues().getObjectAt(0));
        var certificateIds = signingCertificate.getCerts();
        if (certificateIds.length == 0
                || !MessageDigest.isEqual(
                        certificateIds[0].getCertHash(),
                        MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()))) {
            throw new CadesException(
                    "SIGNING_CERTIFICATE_MISMATCH",
                    "signing-certificate-v2 özeti imzalayan sertifikasıyla eşleşmiyor.");
        }
    }

    private static void validateCertificateAtSigningTime(
            org.bouncycastle.cms.SignerInformation signer,
            java.security.cert.X509Certificate certificate) {
        var attributes = signer.getSignedAttributes();
        var signingTime = attributes == null ? null : attributes.get(CMSAttributes.signingTime);
        if (signingTime == null || signingTime.getAttrValues().size() != 1) {
            return;
        }
        var date = Time.getInstance(signingTime.getAttrValues().getObjectAt(0)).getDate();
        try {
            certificate.checkValidity(date);
        } catch (CertificateExpiredException exception) {
            throw new CadesException(
                    "SIGNER_CERTIFICATE_EXPIRED",
                    "İmzalayan sertifikasının imza zamanında geçerlilik süresi dolmuş.",
                    exception);
        } catch (CertificateNotYetValidException exception) {
            throw new CadesException(
                    "SIGNER_CERTIFICATE_NOT_YET_VALID",
                    "İmzalayan sertifikası imza zamanında henüz geçerli değil.",
                    exception);
        }
    }

    private static String verifyPolicy(
            org.bouncycastle.asn1.cms.AttributeTable attributes,
            SignaturePolicy expectedPolicy) {
        var attribute = attributes.get(PKCSObjectIdentifiers.id_aa_ets_sigPolicyId);
        if (expectedPolicy == null) {
            return attribute == null
                    ? null
                    : SignaturePolicyIdentifier.getInstance(
                                    attribute.getAttrValues().getObjectAt(0))
                            .getSignaturePolicyId()
                            .getSigPolicyId()
                            .getId();
        }
        if (attribute == null) {
            throw new CadesException("POLICY_NOT_FOUND", "Beklenen imza politikası imzada bulunamadı.");
        }
        var actual = SignaturePolicyIdentifier.getInstance(
                        attribute.getAttrValues().getObjectAt(0))
                .getSignaturePolicyId();
        if (!expectedPolicy.oid().equals(actual.getSigPolicyId().getId())
                || !expectedPolicy.digestAlgorithmOid()
                        .equals(actual.getSigPolicyHash().getHashAlgorithm().getAlgorithm().getId())
                || !MessageDigest.isEqual(
                        expectedPolicy.digest(),
                        actual.getSigPolicyHash().getHashValue().getOctets())) {
            throw new CadesException(
                    "POLICY_HASH_MISMATCH",
                    "İmza politika OID veya özeti beklenen değerle eşleşmiyor.");
        }
        return actual.getSigPolicyId().getId();
    }
}
