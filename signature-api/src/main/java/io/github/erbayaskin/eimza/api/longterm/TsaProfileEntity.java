package io.github.erbayaskin.eimza.api.longterm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tsa_profile")
class TsaProfileEntity {
    @Id private UUID id;
    @Column(name = "version_number", nullable = false, unique = true)
    private long versionNumber;
    @Column(name = "provider_id", nullable = false, length = 250)
    private String providerId;
    @Column(nullable = false, length = 1000)
    private String endpoint;
    @Column(name = "request_timeout_seconds", nullable = false)
    private int requestTimeoutSeconds;
    @Column(name = "credential_ref", length = 250)
    private String credentialRef;
    @Column(name = "archive_policy_oid", length = 200)
    private String archivePolicyOid;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected TsaProfileEntity() {}

    static TsaProfileEntity create(
            long version, UpdateTsaProfileRequest request, Instant now) {
        var value = new TsaProfileEntity();
        value.id = UUID.randomUUID();
        value.versionNumber = version;
        value.providerId = request.providerId().trim();
        value.endpoint = request.endpoint().trim();
        value.requestTimeoutSeconds = request.requestTimeoutSeconds();
        value.credentialRef = blankToNull(request.credentialRef());
        value.archivePolicyOid = blankToNull(request.archivePolicyOid());
        value.enabled = request.enabled();
        value.createdAt = now;
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    long versionNumber() { return versionNumber; }
    String providerId() { return providerId; }
    String endpoint() { return endpoint; }
    int requestTimeoutSeconds() { return requestTimeoutSeconds; }
    String credentialRef() { return credentialRef; }
    String archivePolicyOid() { return archivePolicyOid; }
    boolean enabled() { return enabled; }
    Instant createdAt() { return createdAt; }
}
