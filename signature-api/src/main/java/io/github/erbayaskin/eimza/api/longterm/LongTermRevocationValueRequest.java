package io.github.erbayaskin.eimza.api.longterm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.github.erbayaskin.eimza.cades.CadesRevocationType;

public record LongTermRevocationValueRequest(
        @NotNull CadesRevocationType type,
        @NotBlank @Size(max = io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_BASE64_REVOCATION_CHARS)
                String value) {
}
