package io.github.erbayaskin.eimza.api.serversigning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateServerKeyProfileRequest(
        @NotBlank @Size(max = 250) String displayName,
        @NotNull ServerDeviceType deviceType,
        @NotBlank @Size(max = 1000) String pkcs11Library,
        Integer slotListIndex,
        @Size(max = 256) String atr,
        @Size(max = 256) String atrMask,
        @Size(max = 128) String certificateFingerprint,
        @Size(max = 250) String credentialRef,
        List<UUID> allowedTenantIds,
        boolean enabled) {
    public CreateServerKeyProfileRequest {
        allowedTenantIds = allowedTenantIds == null ? List.of() : List.copyOf(allowedTenantIds);
    }
}
