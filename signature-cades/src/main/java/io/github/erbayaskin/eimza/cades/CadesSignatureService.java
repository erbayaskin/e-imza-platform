package io.github.erbayaskin.eimza.cades;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerIdentifier;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.esf.OtherHashAlgAndValue;
import org.bouncycastle.asn1.esf.SigPolicyQualifierInfo;
import org.bouncycastle.asn1.esf.SigPolicyQualifiers;
import org.bouncycastle.asn1.esf.SignaturePolicyId;
import org.bouncycastle.asn1.esf.SignaturePolicyIdentifier;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import io.github.erbayaskin.eimza.timestamp.TimestampClient;
import io.github.erbayaskin.eimza.timestamp.TimestampRequest;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;

public class CadesSignatureService {

    private static final String SHA256_OID = "2.16.840.1.101.3.4.2.1";
    private final SecureRandom secureRandom;

    public CadesSignatureService() {
        this(new SecureRandom());
    }

    CadesSignatureService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public CadesSigningPreparation prepare(
            byte[] content,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime) {
        return prepare(
                content, signerCertificate, signatureAlgorithm, signaturePolicy, signingTime, false);
    }

    public CadesSigningPreparation prepare(
            byte[] content,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime,
            boolean attached) {
        return prepare(
                content,
                signerCertificate,
                signatureAlgorithm,
                signaturePolicy,
                signingTime,
                attached,
                true);
    }

    public CadesSigningPreparation prepare(
            byte[] content,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime,
            boolean attached,
            boolean certificateValidityCheckActive) {
        try {
            return prepareDigestInternal(
                    MessageDigest.getInstance(signatureAlgorithm.digestName()).digest(content),
                    signerCertificate,
                    signatureAlgorithm,
                    signaturePolicy,
                    signingTime,
                    attached ? content : null,
                    certificateValidityCheckActive,
                    true);
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException(
                    "CADES_PREPARATION_FAILED", "CAdES imza hazirligi basarisiz.", exception);
        }
    }

    public CadesSigningPreparation prepareDigest(
            byte[] contentDigest,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime) {
        return prepareDigest(
                contentDigest,
                signerCertificate,
                signatureAlgorithm,
                signaturePolicy,
                signingTime,
                true);
    }

    public CadesSigningPreparation prepareDigest(
            byte[] contentDigest,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime,
            boolean certificateValidityCheckActive) {
        return prepareDigestInternal(
                contentDigest,
                signerCertificate,
                signatureAlgorithm,
                signaturePolicy,
                signingTime,
                null,
                certificateValidityCheckActive,
                true);
    }

    /**
     * Prepares an independent CAdES co-signature over the same content.
     */
    public CadesSigningPreparation prepareParallel(
            byte[] existingSignature,
            byte[] content,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime,
            boolean attached,
            boolean certificateValidityCheckActive) {
        requireExistingSignature(existingSignature);
        var prepared = prepare(
                content,
                signerCertificate,
                signatureAlgorithm,
                signaturePolicy,
                signingTime,
                attached,
                certificateValidityCheckActive);
        return multi(prepared, existingSignature, MultiSignatureType.PARALLEL, 0);
    }

    public CadesSigningPreparation prepareParallelDigest(
            byte[] existingSignature,
            byte[] contentDigest,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime,
            boolean certificateValidityCheckActive) {
        requireExistingSignature(existingSignature);
        var prepared = prepareDigest(
                contentDigest,
                signerCertificate,
                signatureAlgorithm,
                signaturePolicy,
                signingTime,
                certificateValidityCheckActive);
        return multi(prepared, existingSignature, MultiSignatureType.PARALLEL, 0);
    }

    /**
     * Prepares a CMS counter-signature over the selected parent SignerInfo value.
     */
    public CadesSigningPreparation prepareCounterSignature(
            byte[] existingSignature,
            int targetSignatureIndex,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime,
            boolean certificateValidityCheckActive) {
        try {
            requireExistingSignature(existingSignature);
            var signedData = signedData(existingSignature);
            var signers = signedData.getSignerInfos();
            if (targetSignatureIndex < 0 || targetSignatureIndex >= signers.size()) {
                throw new CadesException(
                        "CADES_TARGET_SIGNATURE_NOT_FOUND",
                        "Seri imza için hedef CAdES imza indeksi bulunamadı.");
            }
            var parent = SignerInfo.getInstance(signers.getObjectAt(targetSignatureIndex));
            var parentDigest = MessageDigest.getInstance(signatureAlgorithm.digestName())
                    .digest(parent.getEncryptedDigest().getOctets());
            var prepared = prepareDigestInternal(
                    parentDigest,
                    signerCertificate,
                    signatureAlgorithm,
                    signaturePolicy,
                    signingTime,
                    null,
                    certificateValidityCheckActive,
                    false);
            return multi(
                    prepared,
                    existingSignature,
                    MultiSignatureType.SERIAL,
                    targetSignatureIndex);
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException(
                    "CADES_COUNTERSIGN_PREPARATION_FAILED",
                    "CAdES seri imza hazırlığı başarısız.",
                    exception);
        }
    }

    private CadesSigningPreparation prepareDigestInternal(
            byte[] contentDigest,
            X509Certificate signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime,
            byte[] encapsulatedContent,
            boolean certificateValidityCheckActive,
            boolean includeContentType) {
        try {
            validateSignerCertificate(
                    signerCertificate,
                    signatureAlgorithm,
                    signingTime,
                    certificateValidityCheckActive);
            var expectedDigestLength = MessageDigest.getInstance(
                    signatureAlgorithm.digestName()).getDigestLength();
            if (contentDigest == null || contentDigest.length != expectedDigestLength) {
                throw new CadesException(
                        "INVALID_DOCUMENT_DIGEST",
                        signatureAlgorithm.digestName() + " belge özeti "
                                + expectedDigestLength + " bayt olmalıdır.");
            }
            var certificateDigest = MessageDigest.getInstance("SHA-256")
                    .digest(signerCertificate.getEncoded());
            var attributes = new ASN1EncodableVector();
            if (includeContentType) {
                attributes.add(new Attribute(
                        CMSAttributes.contentType, new DERSet(CMSObjectIdentifiers.data)));
            }
            attributes.add(new Attribute(
                    CMSAttributes.messageDigest, new DERSet(new DEROctetString(contentDigest))));
            attributes.add(new Attribute(
                    CMSAttributes.signingTime, new DERSet(new Time(Date.from(signingTime)))));
            attributes.add(new Attribute(
                    PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                    new DERSet(new SigningCertificateV2(new ESSCertIDv2(certificateDigest)))));
            if (signaturePolicy != null) {
                attributes.add(policyAttribute(signaturePolicy));
            }
            var signedAttributes = new DERSet(attributes).getEncoded("DER");
            var digestToSign = MessageDigest.getInstance(
                    signatureAlgorithm.digestName()).digest(signedAttributes);
            return new CadesSigningPreparation(
                    signedAttributes,
                    digestToSign,
                    signerCertificate.getEncoded(),
                    signatureAlgorithm,
                    signaturePolicy,
                    signingTime,
                    encapsulatedContent);
        } catch (CertificateExpiredException exception) {
            throw new CadesException(
                    "SIGNER_CERTIFICATE_EXPIRED",
                    "İmzalayan sertifikasının geçerlilik süresi dolmuş; yeni imza oluşturulamaz.",
                    exception);
        } catch (CertificateNotYetValidException exception) {
            throw new CadesException(
                    "SIGNER_CERTIFICATE_NOT_YET_VALID",
                    "İmzalayan sertifikası henüz geçerli değil; yeni imza oluşturulamaz.",
                    exception);
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException("CADES_PREPARATION_FAILED", "CAdES imza hazırlığı başarısız.", exception);
        }
    }

    public CadesSignatureResult completeBaseline(
            CadesSigningPreparation preparation,
            byte[] rawSignature) {
        try {
            var signerCertificate = certificate(preparation.signerCertificate());
            verifyCardSignature(preparation, rawSignature, signerCertificate);
            var encoded = assemble(preparation, rawSignature, null, signerCertificate);
            return new CadesSignatureResult(
                    encoded,
                    "CAdES",
                    "B-B",
                    "SHA-256",
                    preparation.signatureAlgorithm().name(),
                    preparation.signaturePolicy() == null ? null : preparation.signaturePolicy().oid(),
                    null,
                    null);
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException(
                    "CADES_COMPLETION_FAILED", "CAdES B-B uretimi basarisiz.", exception);
        }
    }

    public CadesSignatureResult completeWithTimestamp(
            CadesSigningPreparation preparation,
            byte[] rawSignature,
            TimestampClient timestampClient,
            String requestedTimestampPolicyOid) {
        try {
            var signerCertificate = certificate(preparation.signerCertificate());
            verifyCardSignature(preparation, rawSignature, signerCertificate);
            var signatureImprint = MessageDigest.getInstance("SHA-256").digest(rawSignature);
            var nonce = new BigInteger(128, secureRandom).setBit(127);
            var timestamp = timestampClient.timestamp(new TimestampRequest(
                    SHA256_OID, signatureImprint, nonce, requestedTimestampPolicyOid));
            var timestampContentInfo = ContentInfo.getInstance(
                    ASN1Primitive.fromByteArray(timestamp.encodedToken()));
            var unsignedAttributes = new DERSet(new Attribute(
                    PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                    new DERSet(timestampContentInfo)));
            var encoded = assemble(preparation, rawSignature, unsignedAttributes, signerCertificate);
            return new CadesSignatureResult(
                    encoded,
                    "CAdES",
                    "B-T",
                    "SHA-256",
                    preparation.signatureAlgorithm().name(),
                    preparation.signaturePolicy() == null ? null : preparation.signaturePolicy().oid(),
                    timestamp.generationTime(),
                    timestamp.policyOid());
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException("CADES_COMPLETION_FAILED", "CAdES B-T üretimi başarısız.", exception);
        }
    }

    private static byte[] assemble(
            CadesSigningPreparation preparation,
            byte[] rawSignature,
            ASN1Set unsignedAttributes,
            X509Certificate certificate) throws Exception {
        if (preparation.multiSignatureType() != MultiSignatureType.SINGLE) {
            return assembleMultiple(preparation, rawSignature, unsignedAttributes, certificate);
        }
        var holder = new X509CertificateHolder(certificate.getEncoded());
        var signerInfo = signerInfo(preparation, rawSignature, unsignedAttributes, certificate);
        var signedData = new SignedData(
                new DERSet(preparation.signatureAlgorithm().digestIdentifier()),
                new ContentInfo(
                        CMSObjectIdentifiers.data,
                        preparation.encapsulatedContent() == null
                                ? null
                                : new DEROctetString(preparation.encapsulatedContent())),
                new DERSet(holder.toASN1Structure()),
                null,
                new DERSet(signerInfo));
        return new ContentInfo(CMSObjectIdentifiers.signedData, signedData).getEncoded("DER");
    }

    private static byte[] assembleMultiple(
            CadesSigningPreparation preparation,
            byte[] rawSignature,
            ASN1Set unsignedAttributes,
            X509Certificate certificate) throws Exception {
        var existing = signedData(preparation.existingSignature());
        var holder = new X509CertificateHolder(certificate.getEncoded());
        var addedSigner = signerInfo(preparation, rawSignature, unsignedAttributes, certificate);
        var algorithms = append(
                existing.getDigestAlgorithms(),
                preparation.signatureAlgorithm().digestIdentifier());
        var certificates = append(existing.getCertificates(), holder.toASN1Structure());
        ASN1Set signers;
        if (preparation.multiSignatureType() == MultiSignatureType.PARALLEL) {
            signers = append(existing.getSignerInfos(), addedSigner);
        } else {
            var values = new ASN1EncodableVector();
            var targetFound = false;
            for (int index = 0; index < existing.getSignerInfos().size(); index++) {
                var current = SignerInfo.getInstance(existing.getSignerInfos().getObjectAt(index));
                if (index == preparation.targetSignatureIndex()) {
                    targetFound = true;
                    var unauthenticated = new ASN1EncodableVector();
                    if (current.getUnauthenticatedAttributes() != null) {
                        for (int attribute = 0;
                                attribute < current.getUnauthenticatedAttributes().size();
                                attribute++) {
                            unauthenticated.add(
                                    current.getUnauthenticatedAttributes().getObjectAt(attribute));
                        }
                    }
                    unauthenticated.add(new Attribute(
                            PKCSObjectIdentifiers.pkcs_9_at_counterSignature,
                            new DERSet(addedSigner)));
                    current = new SignerInfo(
                            current.getSID(),
                            current.getDigestAlgorithm(),
                            current.getAuthenticatedAttributes(),
                            current.getDigestEncryptionAlgorithm(),
                            current.getEncryptedDigest(),
                            new DERSet(unauthenticated));
                }
                values.add(current);
            }
            if (!targetFound) {
                throw new CadesException(
                        "CADES_TARGET_SIGNATURE_NOT_FOUND",
                        "Seri imza için hedef CAdES imzası bulunamadı.");
            }
            signers = new DERSet(values);
        }
        var result = new SignedData(
                algorithms,
                existing.getEncapContentInfo(),
                certificates,
                existing.getCRLs(),
                signers);
        return new ContentInfo(CMSObjectIdentifiers.signedData, result).getEncoded("DER");
    }

    private static SignerInfo signerInfo(
            CadesSigningPreparation preparation,
            byte[] rawSignature,
            ASN1Set unsignedAttributes,
            X509Certificate certificate) throws Exception {
        var holder = new X509CertificateHolder(certificate.getEncoded());
        var signerIdentifier = new SignerIdentifier(new IssuerAndSerialNumber(
                X500Name.getInstance(holder.getIssuer()), holder.getSerialNumber()));
        var signedAttributes = ASN1Set.getInstance(
                ASN1Primitive.fromByteArray(preparation.signedAttributes()));
        return new SignerInfo(
                signerIdentifier,
                preparation.signatureAlgorithm().digestIdentifier(),
                signedAttributes,
                preparation.signatureAlgorithm().cmsIdentifier(),
                new DEROctetString(rawSignature),
                unsignedAttributes);
    }

    private static ASN1Set append(
            ASN1Set source, org.bouncycastle.asn1.ASN1Encodable value) {
        var values = new ASN1EncodableVector();
        if (source != null) {
            for (int index = 0; index < source.size(); index++) {
                values.add(source.getObjectAt(index));
            }
        }
        values.add(value);
        return new DERSet(values);
    }

    private static SignedData signedData(byte[] encoded) throws Exception {
        var contentInfo = ContentInfo.getInstance(ASN1Primitive.fromByteArray(encoded));
        if (!CMSObjectIdentifiers.signedData.equals(contentInfo.getContentType())) {
            throw new CadesException(
                    "CADES_INVALID",
                    "önceki artifact CMS SignedData değildir.");
        }
        return SignedData.getInstance(contentInfo.getContent());
    }

    private static void requireExistingSignature(byte[] existingSignature) {
        if (existingSignature == null || existingSignature.length == 0) {
            throw new CadesException(
                    "EXISTING_SIGNATURE_REQUIRED",
                    "Paralel veya seri CAdES için önceki imza artifact'i zorunludur.");
        }
    }

    private static CadesSigningPreparation multi(
            CadesSigningPreparation preparation,
            byte[] existingSignature,
            MultiSignatureType type,
            int targetSignatureIndex) {
        return new CadesSigningPreparation(
                preparation.signedAttributes(),
                preparation.digestToSign(),
                preparation.signerCertificate(),
                preparation.signatureAlgorithm(),
                preparation.signaturePolicy(),
                preparation.signingTime(),
                preparation.encapsulatedContent(),
                existingSignature,
                type,
                targetSignatureIndex);
    }

    private static void verifyCardSignature(
            CadesSigningPreparation preparation,
            byte[] rawSignature,
            X509Certificate certificate) throws Exception {
        var verifier = Signature.getInstance(preparation.signatureAlgorithm().jcaName());
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(preparation.signedAttributes());
        if (!verifier.verify(rawSignature)) {
            throw new CadesException(
                    "CARD_SIGNATURE_INVALID",
                    "Kart imzası hazırlanmış CAdES imzalı özellikleriyle eşleşmiyor.");
        }
    }

    private static Attribute policyAttribute(SignaturePolicy policy) {
        var hash = new OtherHashAlgAndValue(
                new AlgorithmIdentifier(new ASN1ObjectIdentifier(policy.digestAlgorithmOid())),
                new DEROctetString(policy.digest()));
        SignaturePolicyId policyId;
        if (policy.uri() == null || policy.uri().isBlank()) {
            policyId = new SignaturePolicyId(new ASN1ObjectIdentifier(policy.oid()), hash);
        } else {
            var qualifier = new SigPolicyQualifierInfo(
                    PKCSObjectIdentifiers.id_spq_ets_uri, new DERIA5String(policy.uri()));
            policyId = new SignaturePolicyId(
                    new ASN1ObjectIdentifier(policy.oid()),
                    hash,
                    new SigPolicyQualifiers(new SigPolicyQualifierInfo[] {qualifier}));
        }
        return new Attribute(
                PKCSObjectIdentifiers.id_aa_ets_sigPolicyId,
                new DERSet(new SignaturePolicyIdentifier(policyId)));
    }

    private static X509Certificate certificate(byte[] encoded) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(encoded));
    }

    private static void validateSignerCertificate(
            X509Certificate certificate,
            CadesSignatureAlgorithm algorithm,
            Instant signingTime,
            boolean certificateValidityCheckActive) throws Exception {
        if (certificateValidityCheckActive) {
            certificate.checkValidity(Date.from(signingTime));
        }
        if (!algorithm.keyAlgorithm().equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())) {
            throw new CadesException(
                    "SIGNATURE_KEY_MISMATCH",
                    "Sertifika anahtar türü seçilen imza algoritmasıyla eşleşmiyor.");
        }
        var keyUsage = certificate.getKeyUsage();
        if (keyUsage != null && !keyUsage[0] && !keyUsage[1]) {
            throw new CadesException(
                    "KEY_USAGE_VIOLATION",
                    "Sertifika dijital imza/non-repudiation kullanımına izin vermiyor.");
        }
        if ("RSA".equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())
                && ((java.security.interfaces.RSAPublicKey) certificate.getPublicKey())
                                .getModulus().bitLength()
                        < 2048) {
            throw new CadesException("ALGORITHM_NOT_ALLOWED", "RSA anahtarı en az 2048 bit olmalıdır.");
        }
        if ("EC".equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())
                && ((java.security.interfaces.ECPublicKey) certificate.getPublicKey())
                                .getParams().getCurve().getField().getFieldSize()
                        < 256) {
            throw new CadesException("ALGORITHM_NOT_ALLOWED", "EC anahtarı en az 256 bit olmalıdır.");
        }
    }
}
