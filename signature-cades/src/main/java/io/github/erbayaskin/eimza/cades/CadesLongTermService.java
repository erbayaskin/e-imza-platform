package io.github.erbayaskin.eimza.cades;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.OtherRevocationInfoFormat;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.OCSPResponse;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.CertificateList;
import io.github.erbayaskin.eimza.timestamp.TimestampClient;
import io.github.erbayaskin.eimza.timestamp.TimestampRequest;

/**
 * ETSI EN 319 122-1 V1.3.1 Baseline B-LT/B-LTA augmentation.
 *
 * <p>B-LT material is carried by the root SignedData certificates/crls fields.
 * B-LTA uses archive-time-stamp-v3 with an ATSHashIndexV3 in the timestamp
 * token signer's unsigned attributes.
 */
public final class CadesLongTermService {

    static final ASN1ObjectIdentifier ARCHIVE_TIMESTAMP_V3 =
            new ASN1ObjectIdentifier("0.4.0.1733.2.4");
    static final ASN1ObjectIdentifier ATS_HASH_INDEX_V3 =
            new ASN1ObjectIdentifier("0.4.0.19122.1.5");
    static final ASN1ObjectIdentifier SIGNATURE_TIMESTAMP =
            new ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.14");
    private static final String SHA256_OID = NISTObjectIdentifiers.id_sha256.getId();

    private final SecureRandom secureRandom;

    public CadesLongTermService() {
        this(new SecureRandom());
    }

    CadesLongTermService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public CadesAugmentationResult augmentToBaselineLT(
            byte[] encodedBaselineT,
            CadesValidationMaterial material) {
        try {
            var root = parse(encodedBaselineT);
            requireSingleSignerAndTimestamp(root.signedData());
            if (material.revocationValues().isEmpty()) {
                throw new CadesException(
                        "CADES_LT_REVOCATION_DATA_MISSING",
                        "CAdES B-LT için doğrulamada kullanılan SİL veya OCSP kanıtları zorunludur.");
            }
            var certificates = mergeCertificates(root.signedData().getCertificates(), material.certificates());
            var revocations = mergeRevocations(
                    root.signedData().getCRLs(), material.revocationValues());
            var augmented = rebuild(root.signedData(), certificates, revocations, null);
            return result(augmented, "B-LT", null, null);
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException(
                    "CADES_LT_AUGMENTATION_FAILED",
                    "CAdES B-T imzası B-LT seviyesine yükseltilemedi.",
                    exception);
        }
    }

    public CadesAugmentationResult augmentToBaselineLTA(
            byte[] detachedContent,
            byte[] encodedBaselineLT,
            TimestampClient timestampClient,
            String requestedTimestampPolicyOid) {
        return addArchiveTimestamp(
                detachedContent,
                encodedBaselineLT,
                timestampClient,
                requestedTimestampPolicyOid,
                false);
    }

    public CadesAugmentationResult renewBaselineLTA(
            byte[] detachedContent,
            byte[] encodedBaselineLTA,
            TimestampClient timestampClient,
            String requestedTimestampPolicyOid) {
        return addArchiveTimestamp(
                detachedContent,
                encodedBaselineLTA,
                timestampClient,
                requestedTimestampPolicyOid,
                true);
    }

    private CadesAugmentationResult addArchiveTimestamp(
            byte[] content,
            byte[] encodedSignature,
            TimestampClient timestampClient,
            String requestedPolicy,
            boolean renewal) {
        try {
            var root = parse(encodedSignature);
            var signer = requireSingleSignerAndTimestamp(root.signedData());
            var existingArchiveCount = countAttribute(
                    signer.getUnauthenticatedAttributes(), ARCHIVE_TIMESTAMP_V3);
            if (renewal && existingArchiveCount == 0) {
                throw new CadesException(
                        "CADES_LTA_ARCHIVE_TIMESTAMP_MISSING",
                        "Arşiv zaman damgası yenilemesi için mevcut bir B-LTA imzası gerekir.");
            }
            if (!renewal && existingArchiveCount > 0) {
                throw new CadesException(
                        "CADES_LTA_ALREADY_PRESENT",
                        "İmza zaten arşiv zaman damgası içeriyor; yenileme işlemi kullanılmalıdır.");
            }
            if (root.signedData().getCRLs() == null || root.signedData().getCRLs().size() == 0) {
                throw new CadesException(
                        "CADES_LT_REVOCATION_DATA_MISSING",
                        "B-LTA öncesinde B-LT iptal kanıtları imzaya eklenmelidir.");
            }

            var atsHashIndex = buildAtsHashIndex(root.signedData(), signer);
            var imprintInput = archiveImprintInput(
                    content, root.signedData(), signer, atsHashIndex);
            var imprint = MessageDigest.getInstance("SHA-256").digest(imprintInput);
            var nonce = new BigInteger(128, secureRandom).setBit(127);
            var response = timestampClient.timestamp(new TimestampRequest(
                    SHA256_OID, imprint, nonce, requestedPolicy));
            var timestampWithIndex = addAtsHashIndex(
                    ContentInfo.getInstance(
                            ASN1Primitive.fromByteArray(response.encodedToken())),
                    atsHashIndex);
            var archiveAttribute = new Attribute(
                    ARCHIVE_TIMESTAMP_V3, new DERSet(timestampWithIndex));
            var augmentedSigner = addUnsignedAttribute(signer, archiveAttribute);
            var augmented = rebuild(
                    root.signedData(),
                    root.signedData().getCertificates(),
                    root.signedData().getCRLs(),
                    augmentedSigner);
            return result(
                    augmented,
                    "B-LTA",
                    response.generationTime(),
                    response.policyOid());
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException(
                    "CADES_LTA_AUGMENTATION_FAILED",
                    "CAdES imzasına archive-time-stamp-v3 eklenemedi.",
                    exception);
        }
    }

    private static ASN1Set mergeCertificates(
            ASN1Set existing,
            List<byte[]> additions) throws Exception {
        Map<String, ASN1Encodable> values = encodedSet(existing);
        for (byte[] encoded : additions) {
            var certificate = Certificate.getInstance(
                    ASN1Primitive.fromByteArray(encoded));
            values.putIfAbsent(key(certificate), certificate);
        }
        return new DERSet(values.values().toArray(ASN1Encodable[]::new));
    }

    private static ASN1Set mergeRevocations(
            ASN1Set existing,
            List<CadesRevocationValue> additions) throws Exception {
        Map<String, ASN1Encodable> values = encodedSet(existing);
        for (CadesRevocationValue item : additions) {
            ASN1Encodable value;
            if (item.type() == CadesRevocationType.CRL) {
                value = CertificateList.getInstance(
                        ASN1Primitive.fromByteArray(item.encodedValue()));
            } else {
                var ocsp = OCSPResponse.getInstance(
                        ASN1Primitive.fromByteArray(item.encodedValue()));
                var other = new OtherRevocationInfoFormat(
                        CMSObjectIdentifiers.id_ri_ocsp_response, ocsp);
                value = new DERTaggedObject(false, 1, other);
            }
            values.putIfAbsent(key(value), value);
        }
        return new DERSet(values.values().toArray(ASN1Encodable[]::new));
    }

    private static Map<String, ASN1Encodable> encodedSet(ASN1Set set) throws Exception {
        Map<String, ASN1Encodable> values = new LinkedHashMap<>();
        if (set != null) {
            for (int index = 0; index < set.size(); index++) {
                var value = set.getObjectAt(index);
                values.putIfAbsent(key(value), value);
            }
        }
        return values;
    }

    private static String key(ASN1Encodable value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.toASN1Primitive().getEncoded("DER")));
    }

    private static ASN1Sequence buildAtsHashIndex(
            SignedData signedData,
            SignerInfo signer) throws Exception {
        var certificateHashes = hashesOfSet(signedData.getCertificates());
        var revocationHashes = hashesOfSet(signedData.getCRLs());
        var unsignedHashes = new ASN1EncodableVector();
        var unsigned = signer.getUnauthenticatedAttributes();
        if (unsigned != null) {
            for (int attributeIndex = 0; attributeIndex < unsigned.size(); attributeIndex++) {
                var attribute = Attribute.getInstance(unsigned.getObjectAt(attributeIndex));
                for (int valueIndex = 0; valueIndex < attribute.getAttrValues().size(); valueIndex++) {
                    var bytes = concatenate(
                            attribute.getAttrType().getEncoded("DER"),
                            attribute.getAttrValues()
                                    .getObjectAt(valueIndex)
                                    .toASN1Primitive()
                                    .getEncoded("DER"));
                    unsignedHashes.add(new DEROctetString(sha256(bytes)));
                }
            }
        }
        return new DERSequence(new ASN1Encodable[] {
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
            new DERSequence(certificateHashes),
            new DERSequence(revocationHashes),
            new DERSequence(unsignedHashes)
        });
    }

    private static ASN1EncodableVector hashesOfSet(ASN1Set set) throws Exception {
        var hashes = new ASN1EncodableVector();
        if (set != null) {
            for (int index = 0; index < set.size(); index++) {
                hashes.add(new DEROctetString(sha256(
                        set.getObjectAt(index).toASN1Primitive().getEncoded("DER"))));
            }
        }
        return hashes;
    }

    static byte[] archiveImprintInput(
            byte[] content,
            SignedData signedData,
            SignerInfo signer,
            ASN1Sequence atsHashIndex) throws Exception {
        var output = new ByteArrayOutputStream();
        output.write(signedData.getEncapContentInfo().getContentType().getEncoded("DER"));
        output.write(sha256(content));
        var signerSequence = ASN1Sequence.getInstance(signer.toASN1Primitive());
        int fieldCount = signerSequence.size();
        if (fieldCount > 0
                && signerSequence.getObjectAt(fieldCount - 1) instanceof ASN1TaggedObject tagged
                && tagged.getTagNo() == 1) {
            fieldCount--;
        }
        for (int index = 0; index < fieldCount; index++) {
            output.write(signerSequence.getObjectAt(index)
                    .toASN1Primitive()
                    .getEncoded("DER"));
        }
        output.write(atsHashIndex.getEncoded("DER"));
        return output.toByteArray();
    }

    private static ContentInfo addAtsHashIndex(
            ContentInfo timestamp,
            ASN1Sequence atsHashIndex) {
        var timestampSignedData = SignedData.getInstance(timestamp.getContent());
        if (timestampSignedData.getSignerInfos().size() != 1) {
            throw new CadesException(
                    "TSA_SIGNER_COUNT_INVALID",
                    "Arşiv zaman damgası tam olarak bir TSA imzalayanı içermelidir.");
        }
        var timestampSigner = SignerInfo.getInstance(
                timestampSignedData.getSignerInfos().getObjectAt(0));
        var indexedSigner = addUnsignedAttribute(
                timestampSigner,
                new Attribute(ATS_HASH_INDEX_V3, new DERSet(atsHashIndex)));
        var rebuilt = new SignedData(
                timestampSignedData.getDigestAlgorithms(),
                timestampSignedData.getEncapContentInfo(),
                timestampSignedData.getCertificates(),
                timestampSignedData.getCRLs(),
                new DERSet(indexedSigner));
        return new ContentInfo(CMSObjectIdentifiers.signedData, rebuilt);
    }

    private static SignerInfo addUnsignedAttribute(
            SignerInfo signer,
            Attribute addition) {
        var values = new ASN1EncodableVector();
        var existing = signer.getUnauthenticatedAttributes();
        if (existing != null) {
            for (int index = 0; index < existing.size(); index++) {
                values.add(existing.getObjectAt(index));
            }
        }
        values.add(addition);
        return new SignerInfo(
                signer.getSID(),
                signer.getDigestAlgorithm(),
                signer.getAuthenticatedAttributes(),
                signer.getDigestEncryptionAlgorithm(),
                signer.getEncryptedDigest(),
                new DERSet(values));
    }

    private static byte[] rebuild(
            SignedData source,
            ASN1Set certificates,
            ASN1Set revocations,
            SignerInfo replacementSigner) throws Exception {
        var signers = replacementSigner == null
                ? source.getSignerInfos()
                : new DERSet(replacementSigner);
        var rebuilt = new SignedData(
                source.getDigestAlgorithms(),
                source.getEncapContentInfo(),
                certificates,
                revocations,
                signers);
        return new ContentInfo(CMSObjectIdentifiers.signedData, rebuilt).getEncoded("DER");
    }

    private static ParsedCades parse(byte[] encoded) throws Exception {
        var contentInfo = ContentInfo.getInstance(
                ASN1Primitive.fromByteArray(encoded));
        if (!CMSObjectIdentifiers.signedData.equals(contentInfo.getContentType())) {
            throw new CadesException("CADES_CONTENT_TYPE_INVALID", "CMS SignedData bekleniyor.");
        }
        return new ParsedCades(contentInfo, SignedData.getInstance(contentInfo.getContent()));
    }

    private static SignerInfo requireSingleSignerAndTimestamp(SignedData signedData) {
        if (signedData.getSignerInfos().size() != 1) {
            throw new CadesException(
                    "SIGNER_COUNT_INVALID",
                    "CAdES uzun dönem MVP tam olarak bir imzalayan içermelidir.");
        }
        var signer = SignerInfo.getInstance(signedData.getSignerInfos().getObjectAt(0));
        if (countAttribute(signer.getUnauthenticatedAttributes(), SIGNATURE_TIMESTAMP) == 0) {
            throw new CadesException(
                    "TIMESTAMP_MISSING",
                    "B-LT/B-LTA için signature-time-stamp bulunmalıdır.");
        }
        return signer;
    }

    static int countAttribute(ASN1Set attributes, ASN1ObjectIdentifier oid) {
        int count = 0;
        if (attributes != null) {
            for (int index = 0; index < attributes.size(); index++) {
                var attribute = Attribute.getInstance(attributes.getObjectAt(index));
                if (oid.equals(attribute.getAttrType())) {
                    count += attribute.getAttrValues().size();
                }
            }
        }
        return count;
    }

    private static CadesAugmentationResult result(
            byte[] encoded,
            String level,
            Instant archiveTime,
            String archivePolicy) throws Exception {
        var root = parse(encoded);
        var signer = requireSingleSignerAndTimestamp(root.signedData());
        return new CadesAugmentationResult(
                encoded,
                level,
                root.signedData().getCertificates() == null
                        ? 0
                        : root.signedData().getCertificates().size(),
                root.signedData().getCRLs() == null ? 0 : root.signedData().getCRLs().size(),
                countAttribute(signer.getUnauthenticatedAttributes(), ARCHIVE_TIMESTAMP_V3),
                archiveTime,
                archivePolicy);
    }

    private static byte[] sha256(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    private static byte[] concatenate(byte[]... values) throws Exception {
        var output = new ByteArrayOutputStream();
        for (byte[] value : values) {
            output.write(value);
        }
        return output.toByteArray();
    }

    record ParsedCades(ContentInfo contentInfo, SignedData signedData) {}
}
