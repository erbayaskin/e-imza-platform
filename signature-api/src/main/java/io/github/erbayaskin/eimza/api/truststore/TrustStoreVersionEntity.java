package io.github.erbayaskin.eimza.api.truststore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trust_store_version")
class TrustStoreVersionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String version;

    @Column(name = "content_digest", nullable = false, length = 128)
    private String contentDigest;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TrustStoreVersionEntity() {
    }

    static TrustStoreVersionEntity active(
            UUID id,
            String version,
            String contentDigest,
            Instant validFrom) {
        var entity = new TrustStoreVersionEntity();
        entity.id = id;
        entity.version = version;
        entity.contentDigest = contentDigest;
        entity.status = "ACTIVE";
        entity.validFrom = validFrom;
        entity.createdAt = validFrom;
        return entity;
    }

    void supersede(Instant validUntil) {
        this.status = "SUPERSEDED";
        this.validUntil = validUntil;
    }

    UUID id() {
        return id;
    }

    String version() {
        return version;
    }

    String contentDigest() {
        return contentDigest;
    }

    Instant validFrom() {
        return validFrom;
    }

    Instant validUntil() {
        return validUntil;
    }
}
