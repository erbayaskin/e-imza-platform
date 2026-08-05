package io.github.erbayaskin.eimza.api.validation.policy;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Service
public class ValidationPolicyConfigurationService {

    private static final List<String> MANDATORY_POLICIES = List.of(
            "SIGNATURE_CRYPTOGRAPHIC_INTEGRITY",
            "SIGNED_CONTENT_BINDING",
            "SIGNER_CERTIFICATE_IDENTIFICATION",
            "CERTIFICATE_PATH_AND_TRUST_ANCHOR",
            "ALGORITHM_SECURITY");

    private final ValidationPolicyConfigurationRepository repository;
    private final Environment environment;
    private final Clock clock;

    public ValidationPolicyConfigurationService(
            ValidationPolicyConfigurationRepository repository,
            Environment environment,
            Clock clock) {
        this.repository = repository;
        this.environment = environment;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ValidationPolicyConfigurationResponse current() {
        return repository.findFirstByOrderByVersionNumberDesc()
                .map(value -> policyCustomizationAllowed()
                        ? toResponse(value)
                        : productionLocked(value))
                .orElseGet(this::strictDefault);
    }

    @Transactional
    public ValidationPolicyConfigurationResponse update(UpdateValidationPolicyRequest request) {
        if (request.mode() != ValidationPolicyMode.STRICT && !policyCustomizationAllowed()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "VALIDATION_POLICY_MODE_NOT_ALLOWED",
                    "CUSTOM ve AUDIT_ONLY yalnız local ve test ortamlarında etkinleştirilebilir.",
                    false);
        }
        var previous = repository.findFirstByOrderByVersionNumberDesc();
        var version = previous.map(value -> value.versionNumber() + 1).orElse(1L);
        var allActive = request.mode() == ValidationPolicyMode.STRICT;
        var allPassive = request.mode() == ValidationPolicyMode.AUDIT_ONLY;
        var entity = ValidationPolicyConfigurationEntity.create(
                version,
                request.mode(),
                allActive || (!allPassive && request.qcComplianceActive()),
                allActive || (!allPassive && request.certificatePolicyActive()),
                allActive || (!allPassive && request.revocationActive()),
                allActive || (!allPassive && request.signaturePolicyActive()),
                allActive || (!allPassive && request.signingCertificateValidityActive()),
                clock.instant());
        return toResponse(repository.save(entity));
    }

    public boolean auditOnlyAllowed() {
        return policyCustomizationAllowed();
    }

    public boolean policyCustomizationAllowed() {
        var profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return false;
        }
        return java.util.Arrays.stream(profiles)
                .allMatch(profile -> profile.equals("local") || profile.equals("test"));
    }

    private ValidationPolicyConfigurationResponse strictDefault() {
        return new ValidationPolicyConfigurationResponse(
                0,
                ValidationPolicyMode.STRICT,
                true,
                true,
                true,
                true,
                true,
                auditOnlyAllowed(),
                policyCustomizationAllowed(),
                MANDATORY_POLICIES,
                Instant.EPOCH);
    }

    private ValidationPolicyConfigurationResponse toResponse(
            ValidationPolicyConfigurationEntity value) {
        return new ValidationPolicyConfigurationResponse(
                value.versionNumber(),
                value.mode(),
                value.qcComplianceActive(),
                value.certificatePolicyActive(),
                value.revocationActive(),
                value.signaturePolicyActive(),
                value.signingCertificateValidityActive(),
                auditOnlyAllowed(),
                policyCustomizationAllowed(),
                MANDATORY_POLICIES,
                value.createdAt());
    }

    private ValidationPolicyConfigurationResponse productionLocked(
            ValidationPolicyConfigurationEntity value) {
        return new ValidationPolicyConfigurationResponse(
                value.versionNumber(),
                ValidationPolicyMode.STRICT,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                MANDATORY_POLICIES,
                value.createdAt());
    }
}
