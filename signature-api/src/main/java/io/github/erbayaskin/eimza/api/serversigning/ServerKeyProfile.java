package io.github.erbayaskin.eimza.api.serversigning;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

public record ServerKeyProfile(
        String serverKeyId,
        ServerDeviceType deviceType,
        Path pkcs11Library,
        Integer slotListIndex,
        byte[] atr,
        byte[] atrMask,
        String certificateFingerprint,
        String credentialRef,
        Set<UUID> allowedTenantIds) {

    public ServerKeyProfile {
        atr = atr == null ? null : atr.clone();
        atrMask = atrMask == null ? null : atrMask.clone();
        allowedTenantIds = Set.copyOf(allowedTenantIds);
    }

    @Override
    public byte[] atr() { return atr == null ? null : atr.clone(); }

    @Override
    public byte[] atrMask() { return atrMask == null ? null : atrMask.clone(); }
}
