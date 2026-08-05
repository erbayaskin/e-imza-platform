package io.github.erbayaskin.eimza.validation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CertificateValidationRequest(
        byte[] certificate,
        List<byte[]> intermediateCertificates,
        Instant validationTime,
        String policyVersion,
        CertificateValidationPolicy policy) {

    public CertificateValidationRequest {
        Objects.requireNonNull(certificate, "certificate");
        certificate = certificate.clone();
        intermediateCertificates =
                intermediateCertificates == null
                        ? List.of()
                        : intermediateCertificates.stream().map(byte[]::clone).toList();
        Objects.requireNonNull(validationTime, "validationTime");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(policy, "policy");
    }

    @Override
    public byte[] certificate() {
        return certificate.clone();
    }

    @Override
    public List<byte[]> intermediateCertificates() {
        return intermediateCertificates.stream().map(byte[]::clone).toList();
    }
}
