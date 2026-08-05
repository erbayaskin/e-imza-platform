package io.github.erbayaskin.eimza.api.clientdevice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RegisterClientDeviceRequest(
        @NotNull UUID deviceId,
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Size(max = 256) String publicKey) {}
