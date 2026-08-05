package io.github.erbayaskin.eimza.smartcard.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.smartcard.Atr;
import io.github.erbayaskin.eimza.smartcard.Pkcs11DeviceType;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

class CardProfileRegistryTest {

    @Test
    void selectsConfiguredProfileWithoutCodeChange() {
        var properties = properties(profile("kart-a", "3B950040", "FFFF00FF"));
        var registry = new CardProfileRegistry(properties);

        var selected = registry.match(Atr.fromHex("3B95AA40"));
        assertThat(selected.id()).isEqualTo("kart-a");
        assertThat(selected.deviceType()).isEqualTo(Pkcs11DeviceType.SMART_CARD);
        assertThat(selected.autoDiscoverSlot()).isTrue();
    }

    @Test
    void rejectsHsmBecauseClientAgentOnlySupportsSmartCards() {
        var hsm = hsmProfile("hsm-a", 7);

        assertThatThrownBy(() -> new CardProfileRegistry(properties(hsm)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yalnız SMART_CARD");
    }

    @Test
    void rejectsAmbiguousAtrProfiles() {
        var properties = properties(
                profile("kart-a", "3B950040", "FFFF00FF"),
                profile("kart-b", "3B000040", "FF0000FF"));
        var registry = new CardProfileRegistry(properties);

        assertThatThrownBy(() -> registry.match(Atr.fromHex("3B95AA40")))
                .isInstanceOfSatisfying(
                        AgentException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AMBIGUOUS_CARD_PROFILE"));
    }

    private static AgentProperties properties(AgentProperties.Profile... profiles) {
        var properties = new AgentProperties();
        properties.setCardProfiles(List.of(profiles));
        return properties;
    }

    private static AgentProperties.Profile profile(String id, String atr, String mask) {
        var profile = new AgentProperties.Profile();
        profile.setId(id);
        profile.setDisplayName(id);
        profile.setAtr(atr);
        profile.setAtrMask(mask);
        profile.setPkcs11Library(absoluteLibraryPath(id));
        profile.setAllowedMechanisms(List.of("RSA_PKCS1_SHA256"));
        return profile;
    }

    private static AgentProperties.Profile hsmProfile(String id, Integer slotListIndex) {
        var profile = new AgentProperties.Profile();
        profile.setId(id);
        profile.setDisplayName(id);
        profile.setDeviceType("HSM");
        profile.setPkcs11Library(absoluteLibraryPath(id));
        profile.setSlotListIndex(slotListIndex);
        profile.setAllowedMechanisms(List.of("RSA_PKCS1_SHA256"));
        return profile;
    }

    private static String absoluteLibraryPath(String id) {
        return Path.of(System.getProperty("java.io.tmpdir"), "eimza", "drivers", id + ".pkcs11")
                .toAbsolutePath()
                .normalize()
                .toString();
    }
}
