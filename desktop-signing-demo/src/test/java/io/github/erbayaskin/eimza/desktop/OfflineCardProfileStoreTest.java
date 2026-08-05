package io.github.erbayaskin.eimza.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfflineCardProfileStoreTest {

    @Test
    void profileRoundTripsWithoutPinData() throws Exception {
        var directory = Path.of("target", "offline-profile-" + UUID.randomUUID());
        Files.createDirectories(directory);
        var library = Files.createFile(directory.resolve("akisp11.dll")).toAbsolutePath();
        var store = new OfflineCardProfileStore(directory);
        var profile = new OfflineCardProfile(
                null,
                "AKİS",
                "3B 9F:97-81",
                library);

        store.save(List.of(profile));

        assertThat(store.load()).containsExactly(profile);
        assertThat(Files.readString(store.file()))
                .doesNotContainIgnoringCase("pin=")
                .contains("3B9F9781");
    }

    @Test
    void invalidAtrIsRejected() throws Exception {
        var library = Files.createTempFile(
                Path.of("target"), "pkcs11-", ".dll").toAbsolutePath();

        assertThatThrownBy(() -> new OfflineCardProfile(null, "Kart", "XYZ", library))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ATR");
    }
}
