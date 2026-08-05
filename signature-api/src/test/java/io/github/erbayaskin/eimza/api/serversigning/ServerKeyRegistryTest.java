package io.github.erbayaskin.eimza.api.serversigning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServerKeyRegistryTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void keepsHsmOnConfiguredFixedSlot() {
        var properties = new ServerSigningProperties();
        var profile = new ServerSigningProperties.Profile();
        profile.setServerKeyId("hsm-1");
        profile.setDeviceType(ServerDeviceType.HSM);
        profile.setPkcs11Library(absoluteLibraryPath("vendor.pkcs11"));
        profile.setSlotListIndex(7);
        profile.setCredentialRef("HSM_PIN");
        profile.setAllowedTenantIds(List.of(TENANT.toString()));
        properties.setProfiles(List.of(profile));

        var selected = new ServerKeyRegistry(properties).require("hsm-1", TENANT);

        assertThat(selected.deviceType()).isEqualTo(ServerDeviceType.HSM);
        assertThat(selected.slotListIndex()).isEqualTo(7);
        assertThat(selected.credentialRef()).isEqualTo("HSM_PIN");
    }

    @Test
    void requiresAtrForServerSmartCard() {
        var properties = new ServerSigningProperties();
        var profile = new ServerSigningProperties.Profile();
        profile.setServerKeyId("card-1");
        profile.setDeviceType(ServerDeviceType.SMART_CARD);
        profile.setPkcs11Library(absoluteLibraryPath("card.pkcs11"));
        properties.setProfiles(List.of(profile));

        assertThatThrownBy(() -> new ServerKeyRegistry(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("atr");
    }

    @Test
    void enforcesTenantAllowlist() {
        var properties = new ServerSigningProperties();
        var profile = new ServerSigningProperties.Profile();
        profile.setServerKeyId("hsm-1");
        profile.setDeviceType(ServerDeviceType.HSM);
        profile.setPkcs11Library(absoluteLibraryPath("vendor.pkcs11"));
        profile.setSlotListIndex(7);
        profile.setCredentialRef("HSM_PIN");
        profile.setAllowedTenantIds(List.of(TENANT.toString()));
        properties.setProfiles(List.of(profile));
        var registry = new ServerKeyRegistry(properties);

        assertThatThrownBy(() -> registry.require(
                        "hsm-1", UUID.fromString("22222222-2222-2222-2222-222222222222")))
                .hasMessageContaining("Tenant");
    }

    private static String absoluteLibraryPath(String fileName) {
        return Path.of(System.getProperty("java.io.tmpdir"), "eimza", "test-drivers", fileName)
                .toAbsolutePath()
                .normalize()
                .toString();
    }
}
