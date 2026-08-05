package io.github.erbayaskin.eimza.api.signing;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignatureLevel;

@Entity
@Table(name = "signature_artifact")
class SignatureArtifactEntity {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(name = "artifact_digest", nullable = false) private String artifactDigest;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SignatureFormat format;
    @Enumerated(EnumType.STRING) @Column(name = "signature_level", nullable = false)
    private SignatureLevel level;
    @Column(name = "policy_oid") private String policyOid;
    @Column(name = "signing_certificate_fingerprint", nullable = false)
    private String certificateFingerprint;
    @Column(name = "storage_reference") private String storageReference;
    @Column(name = "encoded_artifact", columnDefinition = "BYTEA") private byte[] encodedArtifact;
    @Column(name = "media_type") private String mediaType;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected SignatureArtifactEntity() {}

    static SignatureArtifactEntity create(
            UUID sessionId, String digest, SignatureFormat format, SignatureLevel level,
            String policyOid, String fingerprint, byte[] artifact, String mediaType, Instant now) {
        var value = new SignatureArtifactEntity();
        value.id = UUID.randomUUID();
        value.sessionId = sessionId;
        value.artifactDigest = digest;
        value.format = format;
        value.level = level;
        value.policyOid = policyOid;
        value.certificateFingerprint = fingerprint;
        value.encodedArtifact = artifact.clone();
        value.mediaType = mediaType;
        value.createdAt = now;
        return value;
    }

    UUID id() { return id; }
    byte[] encodedArtifact() { return encodedArtifact.clone(); }
    String mediaType() { return mediaType; }
    String artifactDigest() { return artifactDigest; }
    SignatureFormat format() { return format; }
    SignatureLevel level() { return level; }
}
