package io.github.erbayaskin.eimza.api.longterm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signature_augmentation")
class SignatureAugmentationEntity {

    @Id
    private UUID id;

    @Column(name = "source_digest", nullable = false, length = 64)
    private String sourceDigest;

    @Column(name = "result_digest", nullable = false, length = 64)
    private String resultDigest;

    @Column(name = "target_level", nullable = false, length = 20)
    private String targetLevel;

    @Column(name = "certificate_count", nullable = false)
    private int certificateCount;

    @Column(name = "revocation_value_count", nullable = false)
    private int revocationValueCount;

    @Column(name = "archive_timestamp_count", nullable = false)
    private int archiveTimestampCount;

    @Column(name = "archive_timestamp_policy_oid", length = 200)
    private String archiveTimestampPolicyOid;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SignatureAugmentationEntity() {
    }

    static SignatureAugmentationEntity create(
            UUID id,
            String sourceDigest,
            String resultDigest,
            String targetLevel,
            int certificateCount,
            int revocationValueCount,
            int archiveTimestampCount,
            String archiveTimestampPolicyOid,
            Instant createdAt) {
        var entity = new SignatureAugmentationEntity();
        entity.id = id;
        entity.sourceDigest = sourceDigest;
        entity.resultDigest = resultDigest;
        entity.targetLevel = targetLevel;
        entity.certificateCount = certificateCount;
        entity.revocationValueCount = revocationValueCount;
        entity.archiveTimestampCount = archiveTimestampCount;
        entity.archiveTimestampPolicyOid = archiveTimestampPolicyOid;
        entity.createdAt = createdAt;
        return entity;
    }
}
