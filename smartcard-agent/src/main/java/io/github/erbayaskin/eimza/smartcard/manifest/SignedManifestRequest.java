package io.github.erbayaskin.eimza.smartcard.manifest;

import jakarta.validation.constraints.NotBlank;

public record SignedManifestRequest(
        @NotBlank String manifest,
        @NotBlank String signature) {}
