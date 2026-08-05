package io.github.erbayaskin.eimza.api.longterm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTsaProfileRequest(
        @NotBlank @Size(max = 250) String providerId,
        @NotBlank @Size(max = 1000) String endpoint,
        @Min(1) @Max(120) int requestTimeoutSeconds,
        @Size(max = 250) String credentialRef,
        @Size(max = 200) String archivePolicyOid,
        boolean enabled) {}
