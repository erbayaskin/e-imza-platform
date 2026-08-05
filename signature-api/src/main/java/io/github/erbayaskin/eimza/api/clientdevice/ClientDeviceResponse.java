package io.github.erbayaskin.eimza.api.clientdevice;

import java.util.UUID;

public record ClientDeviceResponse(
        UUID deviceId, UUID tenantId, String displayName, String algorithm, boolean enabled) {
    static ClientDeviceResponse from(ClientDeviceEntity value) {
        return new ClientDeviceResponse(
                value.deviceId(),
                value.tenantId(),
                value.displayName(),
                "Ed25519",
                value.enabled());
    }
}
