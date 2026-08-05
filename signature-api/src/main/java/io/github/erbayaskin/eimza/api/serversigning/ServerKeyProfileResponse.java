package io.github.erbayaskin.eimza.api.serversigning;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServerKeyProfileResponse(
        UUID id, String serverKeyId, String displayName, ServerDeviceType deviceType,
        String pkcs11Library, Integer slotListIndex, String atr, String atrMask,
        String certificateFingerprint, String credentialRef, List<UUID> allowedTenantIds,
        boolean enabled, Instant createdAt, Instant updatedAt) {
    static ServerKeyProfileResponse from(ServerKeyProfileEntity value) {
        return new ServerKeyProfileResponse(
                value.id(), value.serverKeyId(), value.displayName(), value.deviceType(),
                value.pkcs11Library(), value.slotListIndex(), value.atr(), value.atrMask(),
                value.certificateFingerprint(), value.credentialRef(), value.allowedTenantIds(),
                value.enabled(), value.createdAt(), value.updatedAt());
    }
}
