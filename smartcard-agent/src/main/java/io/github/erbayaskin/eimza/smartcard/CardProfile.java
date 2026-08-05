package io.github.erbayaskin.eimza.smartcard;

import java.nio.file.Path;
import java.util.Set;

public record CardProfile(
        String id,
        String displayName,
        Pkcs11DeviceType deviceType,
        AtrPattern atrPattern,
        Path pkcs11Library,
        int slotListIndex,
        boolean autoDiscoverSlot,
        Set<String> allowedMechanisms) {

    public CardProfile {
        if (id == null || id.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Kart profili kimliği ve adı zorunludur.");
        }
        if (deviceType == null) {
            throw new IllegalArgumentException("PKCS#11 cihaz türü zorunludur.");
        }
        if (deviceType == Pkcs11DeviceType.SMART_CARD && atrPattern == null) {
            throw new IllegalArgumentException("Akıllı kart profili için ATR zorunludur.");
        }
        if (deviceType == Pkcs11DeviceType.HSM && atrPattern != null) {
            throw new IllegalArgumentException("HSM profili ATR içeremez.");
        }
        if (deviceType == Pkcs11DeviceType.SMART_CARD && !autoDiscoverSlot) {
            throw new IllegalArgumentException("Akıllı kart profili slotu otomatik keşfetmelidir.");
        }
        if (deviceType == Pkcs11DeviceType.HSM && autoDiscoverSlot) {
            throw new IllegalArgumentException("HSM profili otomatik slot keşfi kullanamaz.");
        }
        if (pkcs11Library == null || !pkcs11Library.isAbsolute()) {
            throw new IllegalArgumentException("PKCS#11 kitaplığı mutlak bir yol olmalıdır.");
        }
        if (slotListIndex < 0) {
            throw new IllegalArgumentException("slotListIndex negatif olamaz.");
        }
        allowedMechanisms = Set.copyOf(allowedMechanisms);
    }
}
