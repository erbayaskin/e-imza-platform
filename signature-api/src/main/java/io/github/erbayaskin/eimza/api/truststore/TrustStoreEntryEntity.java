package io.github.erbayaskin.eimza.api.truststore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trust_store_entry")
class TrustStoreEntryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trust_store_version_id", nullable = false)
    private TrustStoreVersionEntity trustStoreVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trusted_certificate_id", nullable = false)
    private TrustedCertificateEntity trustedCertificate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_type", nullable = false, length = 20)
    private TrustedCertificateType trustType;

    @Column(name = "display_name", nullable = false, length = 250)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TrustStoreEntryEntity() {
    }

    static TrustStoreEntryEntity create(
            UUID id,
            TrustStoreVersionEntity version,
            TrustedCertificateEntity certificate,
            TrustedCertificateType trustType,
            String displayName,
            boolean enabled,
            Instant createdAt) {
        var entity = new TrustStoreEntryEntity();
        entity.id = id;
        entity.trustStoreVersion = version;
        entity.trustedCertificate = certificate;
        entity.trustType = trustType;
        entity.displayName = displayName;
        entity.enabled = enabled;
        entity.createdAt = createdAt;
        return entity;
    }

    TrustStoreEntryEntity copyTo(TrustStoreVersionEntity version, Instant createdAt) {
        return create(
                UUID.randomUUID(),
                version,
                trustedCertificate,
                trustType,
                displayName,
                enabled,
                createdAt);
    }

    UUID id() {
        return id;
    }

    TrustedCertificateEntity certificate() {
        return trustedCertificate;
    }

    TrustedCertificateType trustType() {
        return trustType;
    }

    String displayName() {
        return displayName;
    }

    boolean enabled() {
        return enabled;
    }
}
