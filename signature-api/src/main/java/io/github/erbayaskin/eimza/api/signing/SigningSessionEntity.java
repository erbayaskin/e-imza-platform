package io.github.erbayaskin.eimza.api.signing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignatureLevel;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;
import io.github.erbayaskin.eimza.core.model.SigningMode;
import io.github.erbayaskin.eimza.core.model.TurkishSignatureProfile;
import io.github.erbayaskin.eimza.core.signing.SigningSessionStatus;

@Entity
@Table(name = "signing_session")
public class SigningSessionEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_id", nullable = false, length = 200)
    private String subjectId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signing_mode", nullable = false, length = 20)
    private SigningMode signingMode;

    @Column(name = "server_key_id", length = 100)
    private String serverKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_packaging", nullable = false, length = 20)
    private SignaturePackaging signaturePackaging;

    @Column(name = "requested_signature_algorithm", length = 40)
    private String requestedSignatureAlgorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "multi_signature_type", nullable = false, length = 16)
    private MultiSignatureType multiSignatureType;

    @Column(name = "existing_artifact", columnDefinition = "BYTEA")
    private byte[] existingArtifact;

    @Column(name = "target_signature_index", nullable = false)
    private int targetSignatureIndex;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SignatureFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", nullable = false, length = 16)
    private SignatureLevel targetLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "turkish_profile", nullable = false, length = 4)
    private TurkishSignatureProfile turkishProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SigningSessionStatus status;

    @Column(name = "document_digest", nullable = false, length = 128)
    private String documentDigest;

    @Column(name = "digest_algorithm", nullable = false, length = 32)
    private String digestAlgorithm;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Column(name = "media_type", nullable = false, length = 100)
    private String mediaType;

    @Column(nullable = false, length = 250)
    private String purpose;

    @Column(name = "document_content", columnDefinition = "BYTEA")
    private byte[] documentContent;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected SigningSessionEntity() {
    }

    public static SigningSessionEntity create(
            UUID id,
            UUID tenantId,
            String subjectId,
            SigningMode signingMode,
            UUID deviceId,
            String serverKeyId,
            SignaturePackaging signaturePackaging,
            String requestedSignatureAlgorithm,
            MultiSignatureType multiSignatureType,
            byte[] existingArtifact,
            int targetSignatureIndex,
            UUID documentId,
            SignatureFormat format,
            SignatureLevel targetLevel,
            TurkishSignatureProfile turkishProfile,
            String digestAlgorithm,
            String documentDigest,
            String documentName,
            String mediaType,
            String purpose,
            byte[] documentContent,
            String idempotencyKey,
            Instant now,
            Instant expiresAt) {
        var entity = new SigningSessionEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.subjectId = subjectId;
        entity.signingMode = signingMode;
        entity.deviceId = deviceId;
        entity.serverKeyId = serverKeyId;
        entity.signaturePackaging = signaturePackaging;
        entity.requestedSignatureAlgorithm = requestedSignatureAlgorithm;
        entity.multiSignatureType = multiSignatureType;
        entity.existingArtifact =
                existingArtifact == null ? null : existingArtifact.clone();
        entity.targetSignatureIndex = targetSignatureIndex;
        entity.documentId = documentId;
        entity.format = format;
        entity.targetLevel = targetLevel;
        entity.turkishProfile = turkishProfile;
        entity.digestAlgorithm = digestAlgorithm;
        entity.documentDigest = documentDigest;
        entity.documentName = documentName;
        entity.mediaType = mediaType;
        entity.purpose = purpose;
        entity.documentContent = documentContent == null ? null : documentContent.clone();
        entity.idempotencyKey = idempotencyKey;
        entity.status = SigningSessionStatus.CREATED;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.expiresAt = expiresAt;
        return entity;
    }

    public void cancel(Instant now) {
        status =
                io.github.erbayaskin.eimza.core.signing.SigningSessionStateMachine.transition(
                        status, SigningSessionStatus.CANCELLED);
        updatedAt = now;
    }

    public void transition(SigningSessionStatus target, Instant now) {
        status = io.github.erbayaskin.eimza.core.signing.SigningSessionStateMachine.transition(status, target);
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String subjectId() {
        return subjectId;
    }

    public UUID deviceId() { return deviceId; }
    public SigningMode signingMode() { return signingMode; }
    public String serverKeyId() { return serverKeyId; }
    public SignaturePackaging signaturePackaging() { return signaturePackaging; }
    public String requestedSignatureAlgorithm() { return requestedSignatureAlgorithm; }
    public MultiSignatureType multiSignatureType() { return multiSignatureType; }
    public byte[] existingArtifact() {
        return existingArtifact == null ? null : existingArtifact.clone();
    }
    public int targetSignatureIndex() { return targetSignatureIndex; }
    public UUID documentId() { return documentId; }
    public String documentDigest() { return documentDigest; }
    public String digestAlgorithm() { return digestAlgorithm; }
    public String documentName() { return documentName; }
    public String mediaType() { return mediaType; }
    public String purpose() { return purpose; }
    public byte[] documentContent() {
        return documentContent == null ? null : documentContent.clone();
    }

    public SignatureFormat format() {
        return format;
    }

    public SignatureLevel targetLevel() {
        return targetLevel;
    }

    public TurkishSignatureProfile turkishProfile() {
        return turkishProfile;
    }

    public SigningSessionStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
