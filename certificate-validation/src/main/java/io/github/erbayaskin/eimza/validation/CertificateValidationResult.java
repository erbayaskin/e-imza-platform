package io.github.erbayaskin.eimza.validation;

import java.time.Instant;
import java.util.List;
import io.github.erbayaskin.eimza.core.model.ValidationIndication;

public record CertificateValidationResult(
        ValidationIndication cryptographicValidity,
        ValidationIndication turkishQualification,
        Instant validationTime,
        String policyVersion,
        String trustStoreVersion,
        List<String> certificationPath,
        List<ValidationCheck> checks) {

    public CertificateValidationResult {
        certificationPath = List.copyOf(certificationPath);
        checks = List.copyOf(checks);
    }
}
