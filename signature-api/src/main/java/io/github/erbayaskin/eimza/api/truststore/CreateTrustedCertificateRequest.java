package io.github.erbayaskin.eimza.api.truststore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTrustedCertificateRequest(
        @NotBlank String certificate,
        @NotNull TrustedCertificateType trustType,
        @NotBlank @Size(max = 250) String displayName,
        boolean enabled) {
}
