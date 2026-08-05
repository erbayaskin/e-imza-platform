package io.github.erbayaskin.eimza.api.signing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteSigningSessionRequest(
        @NotBlank @Size(max = 16384) String signature,
        @NotBlank @Size(max = 512) String deviceSignature) {}
