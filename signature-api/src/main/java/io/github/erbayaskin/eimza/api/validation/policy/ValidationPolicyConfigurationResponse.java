package io.github.erbayaskin.eimza.api.validation.policy;

import java.time.Instant;
import java.util.List;

public record ValidationPolicyConfigurationResponse(
        long version,
        ValidationPolicyMode mode,
        boolean qcComplianceActive,
        boolean certificatePolicyActive,
        boolean revocationActive,
        boolean signaturePolicyActive,
        boolean signingCertificateValidityActive,
        boolean auditOnlyAllowed,
        boolean policyCustomizationAllowed,
        List<String> mandatoryPolicies,
        Instant createdAt) {

    public ValidationPolicyConfigurationResponse {
        mandatoryPolicies = List.copyOf(mandatoryPolicies);
    }
}
