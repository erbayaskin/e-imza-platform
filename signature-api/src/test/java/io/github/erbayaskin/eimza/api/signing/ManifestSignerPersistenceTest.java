package io.github.erbayaskin.eimza.api.signing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ManifestSignerPersistenceTest {

    @Test
    void reusesLocalManifestKeyAcrossApplicationRestarts() throws Exception {
        var directory = Path.of("target", "manifest-test-" + UUID.randomUUID());
        var base = directory.resolve("manifest-ed25519");
        try {
            var properties = new ManifestSigningProperties();
            properties.setLocalKeyPath(base.toString());
            var environment = new MockEnvironment();
            environment.setActiveProfiles("local");

            var first = new ManifestSigner(properties, environment);
            var second = new ManifestSigner(properties, environment);

            assertThat(second.publicKey()).isEqualTo(first.publicKey());
            assertThat(second.keyId()).isEqualTo(first.keyId());
            assertThat(second.sign("manifest".getBytes()))
                    .isEqualTo(first.sign("manifest".getBytes()));
        } finally {
            Files.deleteIfExists(Path.of(base + ".pk8"));
            Files.deleteIfExists(Path.of(base + ".spki"));
            Files.deleteIfExists(directory);
        }
    }
}
