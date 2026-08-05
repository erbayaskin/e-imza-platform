package io.github.erbayaskin.eimza.cades;

import java.security.MessageDigest;
import java.security.cert.TrustAnchor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import io.github.erbayaskin.eimza.timestamp.TimestampTokenVerifier;

public final class CadesLongTermVerifier {

    private static final String SHA256_OID = NISTObjectIdentifiers.id_sha256.getId();
    private final TimestampTokenVerifier timestampVerifier = new TimestampTokenVerifier();

    public CadesLongTermVerificationResult verify(
            byte[] detachedContent,
            byte[] encodedSignature,
            Set<TrustAnchor> tsaTrustAnchors) {
        try {
            var contentInfo = ContentInfo.getInstance(
                    ASN1Primitive.fromByteArray(encodedSignature));
            var signedData = SignedData.getInstance(contentInfo.getContent());
            if (signedData.getSignerInfos().size() != 1) {
                throw new CadesException(
                        "SIGNER_COUNT_INVALID",
                        "CAdES uzun dönem doğrulaması tam olarak bir imzalayan destekler.");
            }
            var signer = SignerInfo.getInstance(signedData.getSignerInfos().getObjectAt(0));
            int certificateCount =
                    signedData.getCertificates() == null ? 0 : signedData.getCertificates().size();
            int revocationCount = signedData.getCRLs() == null ? 0 : signedData.getCRLs().size();
            var archiveValues = attributeValues(
                    signer.getUnauthenticatedAttributes(),
                    CadesLongTermService.ARCHIVE_TIMESTAMP_V3);
            var archiveTokens = archiveValues.stream()
                    .map(ContentInfo::getInstance)
                    .toList();

            if (archiveTokens.isEmpty()) {
                return new CadesLongTermVerificationResult(
                        revocationCount > 0 ? "B-LT" : "B-T",
                        certificateCount,
                        revocationCount,
                        List.of(),
                        List.of());
            }
            if (revocationCount == 0) {
                throw new CadesException(
                        "CADES_LTA_REVOCATION_DATA_MISSING",
                        "B-LTA arşiv zaman damgasının koruduğu iptal kanıtı bulunamadı.");
            }

            var archiveTimes = new ArrayList<java.time.Instant>();
            var archivePolicies = new ArrayList<String>();
            for (ContentInfo archiveToken : archiveTokens) {
                var atsHashIndex = extractAtsHashIndex(archiveToken);
                verifyAtsHashIndex(atsHashIndex, signedData, signer);
                var imprintInput = CadesLongTermService.archiveImprintInput(
                        detachedContent, signedData, signer, atsHashIndex);
                var imprint = MessageDigest.getInstance("SHA-256").digest(imprintInput);
                var verified = timestampVerifier.verify(
                        archiveToken.getEncoded("DER"),
                        SHA256_OID,
                        imprint,
                        null,
                        null,
                        "embedded-archive",
                        tsaTrustAnchors);
                archiveTimes.add(verified.generationTime());
                archivePolicies.add(verified.policyOid());
            }
            return new CadesLongTermVerificationResult(
                    "B-LTA",
                    certificateCount,
                    revocationCount,
                    archiveTimes,
                    archivePolicies);
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException(
                    "CADES_LONG_TERM_INVALID",
                    "CAdES uzun dönem doğrulama materyali geçersiz.",
                    exception);
        }
    }

    private static ASN1Sequence extractAtsHashIndex(ContentInfo archiveToken) {
        var tokenSignedData = SignedData.getInstance(archiveToken.getContent());
        if (tokenSignedData.getSignerInfos().size() != 1) {
            throw new CadesException(
                    "TSA_SIGNER_COUNT_INVALID",
                    "Arşiv zaman damgası tek TSA imzalayanı içermelidir.");
        }
        var tokenSigner = SignerInfo.getInstance(
                tokenSignedData.getSignerInfos().getObjectAt(0));
        var matches = attributeValues(
                tokenSigner.getUnauthenticatedAttributes(),
                CadesLongTermService.ATS_HASH_INDEX_V3);
        if (matches.size() != 1) {
            throw new CadesException(
                    "ATS_HASH_INDEX_INVALID",
                    "Arşiv zaman damgası tam olarak bir ats-hash-index-v3 içermelidir.");
        }
        return ASN1Sequence.getInstance(matches.getFirst());
    }

    private static void verifyAtsHashIndex(
            ASN1Sequence index,
            SignedData signedData,
            SignerInfo signer) throws Exception {
        if (index.size() != 4) {
            throw new CadesException(
                    "ATS_HASH_INDEX_INVALID",
                    "ATSHashIndexV3 dört bileşen içermelidir.");
        }
        var algorithm = org.bouncycastle.asn1.x509.AlgorithmIdentifier.getInstance(
                index.getObjectAt(0));
        if (!NISTObjectIdentifiers.id_sha256.equals(algorithm.getAlgorithm())) {
            throw new CadesException(
                    "ATS_HASH_ALGORITHM_UNSUPPORTED",
                    "Faz 7 ATSHashIndexV3 politikası SHA-256 gerektirir.");
        }
        verifyReferencedHashes(
                ASN1Sequence.getInstance(index.getObjectAt(1)),
                componentHashes(signedData.getCertificates()),
                "ATS_CERTIFICATE_HASH_MISMATCH");
        verifyReferencedHashes(
                ASN1Sequence.getInstance(index.getObjectAt(2)),
                componentHashes(signedData.getCRLs()),
                "ATS_REVOCATION_HASH_MISMATCH");
        verifyReferencedHashes(
                ASN1Sequence.getInstance(index.getObjectAt(3)),
                unsignedAttributeValueHashes(signer.getUnauthenticatedAttributes()),
                "ATS_UNSIGNED_ATTRIBUTE_HASH_MISMATCH");
    }

    private static Set<String> componentHashes(ASN1Set values) throws Exception {
        var hashes = new HashSet<String>();
        if (values != null) {
            for (int index = 0; index < values.size(); index++) {
                hashes.add(hex(sha256(values.getObjectAt(index)
                        .toASN1Primitive()
                        .getEncoded("DER"))));
            }
        }
        return hashes;
    }

    private static Set<String> unsignedAttributeValueHashes(ASN1Set values) throws Exception {
        var hashes = new HashSet<String>();
        if (values != null) {
            for (int attributeIndex = 0; attributeIndex < values.size(); attributeIndex++) {
                var attribute = Attribute.getInstance(values.getObjectAt(attributeIndex));
                for (int valueIndex = 0;
                        valueIndex < attribute.getAttrValues().size();
                        valueIndex++) {
                    var digest = MessageDigest.getInstance("SHA-256");
                    digest.update(attribute.getAttrType().getEncoded("DER"));
                    digest.update(attribute.getAttrValues()
                            .getObjectAt(valueIndex)
                            .toASN1Primitive()
                            .getEncoded("DER"));
                    hashes.add(hex(digest.digest()));
                }
            }
        }
        return hashes;
    }

    private static void verifyReferencedHashes(
            ASN1Sequence referenced,
            Set<String> available,
            String errorCode) {
        for (int index = 0; index < referenced.size(); index++) {
            var hash = ASN1OctetString.getInstance(referenced.getObjectAt(index)).getOctets();
            if (!available.contains(hex(hash))) {
                throw new CadesException(
                        errorCode,
                        "ATSHashIndexV3 tarafından referanslanan bileşen imzada bulunamadı.");
            }
        }
    }

    private static List<org.bouncycastle.asn1.ASN1Encodable> attributeValues(
            ASN1Set attributes,
            ASN1ObjectIdentifier oid) {
        var values = new ArrayList<org.bouncycastle.asn1.ASN1Encodable>();
        if (attributes != null) {
            for (int index = 0; index < attributes.size(); index++) {
                var attribute = Attribute.getInstance(attributes.getObjectAt(index));
                if (oid.equals(attribute.getAttrType())) {
                    for (int valueIndex = 0;
                            valueIndex < attribute.getAttrValues().size();
                            valueIndex++) {
                        values.add(attribute.getAttrValues().getObjectAt(valueIndex));
                    }
                }
            }
        }
        return values;
    }

    private static byte[] sha256(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    private static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }
}
