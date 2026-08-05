package io.github.erbayaskin.eimza.api.signing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signing_preparation")
class SigningPreparationEntity {
    @Id
    @Column(name = "session_id")
    private UUID sessionId;
    @Column(name = "preparation_type", nullable = false)
    private String preparationType;
    @Column(name = "preparation_json", nullable = false, columnDefinition = "TEXT")
    private String preparationJson;
    @Column(name = "signing_certificate_fingerprint", nullable = false)
    private String certificateFingerprint;
    @Column(name = "signing_certificate", nullable = false, columnDefinition = "BYTEA")
    private byte[] certificate;
    @Column(name = "signature_algorithm", nullable = false)
    private String signatureAlgorithm;
    @Column(name = "reader_id", nullable = false)
    private String readerId;
    @Column(name = "manifest_nonce", nullable = false)
    private String nonce;
    @Column(name = "manifest_json", nullable = false, columnDefinition = "TEXT")
    private String manifestJson;
    @Column(name = "manifest_signature", nullable = false)
    private String manifestSignature;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SigningPreparationEntity() {}

    static SigningPreparationEntity create(
            UUID sessionId, String preparationType, String preparationJson,
            String fingerprint, byte[] certificate, String algorithm, String readerId,
            String nonce, String manifestJson, String manifestSignature, Instant createdAt) {
        var value = new SigningPreparationEntity();
        value.sessionId = sessionId;
        value.preparationType = preparationType;
        value.preparationJson = preparationJson;
        value.certificateFingerprint = fingerprint;
        value.certificate = certificate.clone();
        value.signatureAlgorithm = algorithm;
        value.readerId = readerId;
        value.nonce = nonce;
        value.manifestJson = manifestJson;
        value.manifestSignature = manifestSignature;
        value.createdAt = createdAt;
        return value;
    }

    String preparationType() { return preparationType; }
    String preparationJson() { return preparationJson; }
    String certificateFingerprint() { return certificateFingerprint; }
    byte[] certificate() { return certificate.clone(); }
    String signatureAlgorithm() { return signatureAlgorithm; }
    String manifestJson() { return manifestJson; }
    String manifestSignature() { return manifestSignature; }
    String nonce() { return nonce; }
}
