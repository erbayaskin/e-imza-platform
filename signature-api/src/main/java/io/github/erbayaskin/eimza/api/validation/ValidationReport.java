package io.github.erbayaskin.eimza.api.validation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.github.erbayaskin.eimza.api.validation.policy.ValidationPolicyMode;
import io.github.erbayaskin.eimza.core.model.ValidationIndication;
import io.github.erbayaskin.eimza.validation.ValidationCheck;

public record ValidationReport(
        UUID validationId,
        String targetType,
        ValidationIndication mainIndication,
        ValidationIndication cryptographicValidity,
        ValidationIndication turkishQualifiedSignatureCompliance,
        Instant validationTime,
        String policyVersion,
        ValidationPolicyMode policyMode,
        List<String> passivePolicies,
        String trustStoreVersion,
        String detectedFormat,
        String detectedLevel,
        SignerCertificateInfo signer,
        List<String> certificationPath,
        List<ValidationCheck> checks,
        String summary) {

    public ValidationReport {
        certificationPath = List.copyOf(certificationPath);
        checks = List.copyOf(checks);
        passivePolicies = List.copyOf(passivePolicies);
    }
}
