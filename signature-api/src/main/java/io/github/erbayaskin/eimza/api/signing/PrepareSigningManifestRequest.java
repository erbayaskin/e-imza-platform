package io.github.erbayaskin.eimza.api.signing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrepareSigningManifestRequest(
        @NotBlank @Size(max = 300) String readerId,
        @NotBlank @Size(max = 64) String certificateFingerprint,
        @NotBlank @Size(max = 32768) String certificateBase64,
        @NotBlank String signatureAlgorithm) {}
