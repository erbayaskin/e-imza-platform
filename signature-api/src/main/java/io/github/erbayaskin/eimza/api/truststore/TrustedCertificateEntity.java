package io.github.erbayaskin.eimza.api.truststore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trusted_certificate")
class TrustedCertificateEntity {

    @Id
    private UUID id;

    @Column(name = "fingerprint_sha256", nullable = false, unique = true, length = 64)
    private String fingerprintSha256;

    @Column(name = "subject_dn", nullable = false, length = 2000)
    private String subjectDn;

    @Column(name = "issuer_dn", nullable = false, length = 2000)
    private String issuerDn;

    @Column(name = "serial_number_hex", nullable = false, length = 256)
    private String serialNumberHex;

    @Column(name = "not_before", nullable = false)
    private Instant notBefore;

    @Column(name = "not_after", nullable = false)
    private Instant notAfter;

    @Column(name = "certificate_base64", nullable = false, columnDefinition = "TEXT")
    private String certificateBase64;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TrustedCertificateEntity() {
    }

    static TrustedCertificateEntity create(
            UUID id,
            ParsedTrustedCertificate certificate,
            Instant createdAt) {
        var entity = new TrustedCertificateEntity();
        entity.id = id;
        entity.fingerprintSha256 = certificate.fingerprintSha256();
        entity.subjectDn = certificate.subjectDn();
        entity.issuerDn = certificate.issuerDn();
        entity.serialNumberHex = certificate.serialNumberHex();
        entity.notBefore = certificate.notBefore();
        entity.notAfter = certificate.notAfter();
        entity.certificateBase64 = certificate.certificateBase64();
        entity.createdAt = createdAt;
        return entity;
    }

    UUID id() {
        return id;
    }

    String fingerprintSha256() {
        return fingerprintSha256;
    }

    String subjectDn() {
        return subjectDn;
    }

    String issuerDn() {
        return issuerDn;
    }

    String serialNumberHex() {
        return serialNumberHex;
    }

    Instant notBefore() {
        return notBefore;
    }

    Instant notAfter() {
        return notAfter;
    }

    String certificateBase64() {
        return certificateBase64;
    }
}
