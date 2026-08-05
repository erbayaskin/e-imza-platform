package io.github.erbayaskin.eimza.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import io.github.erbayaskin.eimza.smartcard.Atr;
import io.github.erbayaskin.eimza.smartcard.AtrPattern;
import io.github.erbayaskin.eimza.smartcard.CardProfile;
import io.github.erbayaskin.eimza.smartcard.Pkcs11DeviceType;

record OfflineCardProfile(String id, String displayName, String atr, Path pkcs11Library) {

    private static final Set<String> MECHANISMS = Set.copyOf(Arrays.stream(
                    io.github.erbayaskin.eimza.cades.CadesSignatureAlgorithm.values())
            .map(Enum::name)
            .toList());

    OfflineCardProfile {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Kart adı zorunludur.");
        }
        var parsedAtr = Atr.fromHex(atr);
        atr = parsedAtr.hex();
        if (pkcs11Library == null) {
            throw new IllegalArgumentException("PKCS#11 kütüphane dosyası zorunludur.");
        }
        pkcs11Library = pkcs11Library.toAbsolutePath().normalize();
        if (!Files.isRegularFile(pkcs11Library)) {
            throw new IllegalArgumentException(
                    "PKCS#11 kütüphane dosyası bulunamadı: " + pkcs11Library);
        }
        displayName = displayName.trim();
    }

    CardProfile toLibraryProfile() {
        var value = Atr.fromHex(atr);
        var mask = Atr.fromHex("FF".repeat(value.bytes().length));
        return new CardProfile(
                id,
                displayName,
                Pkcs11DeviceType.SMART_CARD,
                new AtrPattern(value, mask),
                pkcs11Library,
                0,
                true,
                MECHANISMS);
    }

    boolean matches(String candidateAtr) {
        return toLibraryProfile().atrPattern().matches(Atr.fromHex(candidateAtr));
    }

    @Override
    public String toString() {
        return displayName + " — " + atr;
    }
}
