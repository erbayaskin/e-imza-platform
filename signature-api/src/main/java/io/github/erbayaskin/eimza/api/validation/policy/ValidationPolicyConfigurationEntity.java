package io.github.erbayaskin.eimza.api.validation.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_policy_configuration")
class ValidationPolicyConfigurationEntity {

    @Id
    private UUID id;

    @Column(name = "version_number", nullable = false, unique = true)
    private long versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ValidationPolicyMode mode;

    @Column(name = "qc_compliance_active", nullable = false)
    private boolean qcComplianceActive;

    @Column(name = "certificate_policy_active", nullable = false)
    private boolean certificatePolicyActive;

    @Column(name = "revocation_active", nullable = false)
    private boolean revocationActive;

    @Column(name = "signature_policy_active", nullable = false)
    private boolean signaturePolicyActive;

    @Column(name = "signing_certificate_validity_active", nullable = false)
    private boolean signingCertificateValidityActive;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ValidationPolicyConfigurationEntity() {}

    static ValidationPolicyConfigurationEntity create(
            long versionNumber,
            ValidationPolicyMode mode,
            boolean qcComplianceActive,
            boolean certificatePolicyActive,
            boolean revocationActive,
            boolean signaturePolicyActive,
            boolean signingCertificateValidityActive,
            Instant createdAt) {
        var value = new ValidationPolicyConfigurationEntity();
        value.id = UUID.randomUUID();
        value.versionNumber = versionNumber;
        value.mode = mode;
        value.qcComplianceActive = qcComplianceActive;
        value.certificatePolicyActive = certificatePolicyActive;
        value.revocationActive = revocationActive;
        value.signaturePolicyActive = signaturePolicyActive;
        value.signingCertificateValidityActive = signingCertificateValidityActive;
        value.createdAt = createdAt;
        return value;
    }

    long versionNumber() { return versionNumber; }
    ValidationPolicyMode mode() { return mode; }
    boolean qcComplianceActive() { return qcComplianceActive; }
    boolean certificatePolicyActive() { return certificatePolicyActive; }
    boolean revocationActive() { return revocationActive; }
    boolean signaturePolicyActive() { return signaturePolicyActive; }
    boolean signingCertificateValidityActive() { return signingCertificateValidityActive; }
    Instant createdAt() { return createdAt; }
}
