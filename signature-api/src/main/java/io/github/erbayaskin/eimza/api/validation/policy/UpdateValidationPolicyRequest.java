package io.github.erbayaskin.eimza.api.validation.policy;

import jakarta.validation.constraints.NotNull;

public record UpdateValidationPolicyRequest(
        @NotNull ValidationPolicyMode mode,
        boolean qcComplianceActive,
        boolean certificatePolicyActive,
        boolean revocationActive,
        boolean signaturePolicyActive,
        boolean signingCertificateValidityActive) {}
