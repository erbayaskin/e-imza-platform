package io.github.erbayaskin.eimza.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class OfflineCardProfileStore {

    private static final String IDS = "profiles";
    private final Path dataDirectory;
    private final Path file;

    OfflineCardProfileStore(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.file = this.dataDirectory.resolve("card-profiles.properties");
        Files.createDirectories(this.dataDirectory);
    }

    synchronized List<OfflineCardProfile> load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        var properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        var profiles = new ArrayList<OfflineCardProfile>();
        for (var id : properties.getProperty(IDS, "").split(",")) {
            if (id.isBlank()) {
                continue;
            }
            var prefix = "profile." + id + ".";
            profiles.add(new OfflineCardProfile(
                    id,
                    required(properties, prefix + "name"),
                    required(properties, prefix + "atr"),
                    Path.of(required(properties, prefix + "library"))));
        }
        return List.copyOf(profiles);
    }

    synchronized void save(List<OfflineCardProfile> profiles) throws IOException {
        var properties = new Properties();
        properties.setProperty(IDS, String.join(",", profiles.stream()
                .map(OfflineCardProfile::id)
                .toList()));
        for (var profile : profiles) {
            var prefix = "profile." + profile.id() + ".";
            properties.setProperty(prefix + "name", profile.displayName());
            properties.setProperty(prefix + "atr", profile.atr());
            properties.setProperty(prefix + "library", profile.pkcs11Library().toString());
        }
        Files.createDirectories(dataDirectory);
        var temporary = Files.createTempFile(dataDirectory, "card-profiles-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "E-Imza offline masaustu kart profilleri; PIN saklanmaz.");
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Path file() {
        return file;
    }

    private static String required(Properties properties, String key) {
        var value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Kart profilinde eksik alan: " + key);
        }
        return value;
    }
}
