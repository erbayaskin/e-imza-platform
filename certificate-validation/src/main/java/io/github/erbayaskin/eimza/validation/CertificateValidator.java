package io.github.erbayaskin.eimza.validation;

public interface CertificateValidator {

    CertificateValidationResult validate(CertificateValidationRequest request);
}
