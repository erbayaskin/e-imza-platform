package io.github.erbayaskin.eimza.smartcard.profile;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.Atr;
import io.github.erbayaskin.eimza.smartcard.AtrPattern;
import io.github.erbayaskin.eimza.smartcard.CardProfile;
import io.github.erbayaskin.eimza.smartcard.Pkcs11DeviceType;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

@Component
public class CardProfileRegistry {

    private final List<CardProfile> profiles;

    public CardProfileRegistry(AgentProperties properties) {
        this.profiles = properties.getCardProfiles().stream()
                .map(CardProfileRegistry::toProfile)
                .toList();
    }

    private static CardProfile toProfile(AgentProperties.Profile item) {
        var deviceType = parseDeviceType(item.getDeviceType());
        if (deviceType != Pkcs11DeviceType.SMART_CARD) {
            throw new IllegalArgumentException(
                    "Client-side Smart Card Agent yalnız SMART_CARD profillerini destekler; HSM server-side yapılandırılmalıdır.");
        }
        var library = Path.of(item.getPkcs11Library());
        if (!library.isAbsolute()) {
            throw new IllegalArgumentException("PKCS#11 kitaplığı mutlak bir yol olmalıdır.");
        }
        var atrPattern = deviceType == Pkcs11DeviceType.SMART_CARD
                ? smartCardAtrPattern(item)
                : hsmAtrPattern(item);
        var autoDiscoverSlot = item.getAutoDiscoverSlot() == null
                ? deviceType == Pkcs11DeviceType.SMART_CARD
                : item.getAutoDiscoverSlot();
        if (deviceType == Pkcs11DeviceType.SMART_CARD && !autoDiscoverSlot) {
            throw new IllegalArgumentException(
                    "SMART_CARD profillerinde auto-discover-slot false olamaz.");
        }
        if (deviceType == Pkcs11DeviceType.HSM && autoDiscoverSlot) {
            throw new IllegalArgumentException(
                    "HSM profillerinde auto-discover-slot true olamaz.");
        }
        if (deviceType == Pkcs11DeviceType.HSM && item.getSlotListIndex() == null) {
            throw new IllegalArgumentException(
                    "HSM profillerinde slot-list-index zorunludur.");
        }
        return new CardProfile(
                item.getId(),
                item.getDisplayName(),
                deviceType,
                atrPattern,
                library.normalize(),
                item.getSlotListIndex() == null ? 0 : item.getSlotListIndex(),
                autoDiscoverSlot,
                Set.copyOf(item.getAllowedMechanisms()));
    }

    private static Pkcs11DeviceType parseDeviceType(String value) {
        try {
            return Pkcs11DeviceType.valueOf(value == null ? "SMART_CARD" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "device-type yalnız SMART_CARD veya HSM olabilir.", exception);
        }
    }

    private static AtrPattern smartCardAtrPattern(AgentProperties.Profile item) {
        if (item.getAtr() == null || item.getAtr().isBlank()
                || item.getAtrMask() == null || item.getAtrMask().isBlank()) {
            throw new IllegalArgumentException(
                    "SMART_CARD profillerinde atr ve atr-mask zorunludur.");
        }
        return new AtrPattern(Atr.fromHex(item.getAtr()), Atr.fromHex(item.getAtrMask()));
    }

    private static AtrPattern hsmAtrPattern(AgentProperties.Profile item) {
        if ((item.getAtr() != null && !item.getAtr().isBlank())
                || (item.getAtrMask() != null && !item.getAtrMask().isBlank())) {
            throw new IllegalArgumentException("HSM profilleri atr veya atr-mask içeremez.");
        }
        return null;
    }

    public CardProfile match(Atr atr) {
        var matches = profiles.stream()
                .filter(profile -> profile.deviceType() == Pkcs11DeviceType.SMART_CARD)
                .filter(profile -> profile.atrPattern().matches(atr))
                .toList();
        if (matches.isEmpty()) {
            throw new AgentException("UNSUPPORTED_CARD", "Kart ATR değeri tanımlı bir profille eşleşmedi.");
        }
        if (matches.size() > 1) {
            throw new AgentException("AMBIGUOUS_CARD_PROFILE", "Kart ATR değeri birden fazla profille eşleşti.");
        }
        return matches.getFirst();
    }

    public List<CardProfile> profiles() {
        return profiles;
    }

    public CardProfile byId(String id) {
        return profiles.stream()
                .filter(profile -> profile.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AgentException("CARD_PROFILE_NOT_FOUND", "Kart profili bulunamadı."));
    }
}
