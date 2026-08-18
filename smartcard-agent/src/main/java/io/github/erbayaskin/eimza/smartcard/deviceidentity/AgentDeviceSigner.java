package io.github.erbayaskin.eimza.smartcard.deviceidentity;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

@Component
public class AgentDeviceSigner {
    private final String deviceId;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String persistence;

    public AgentDeviceSigner(AgentProperties properties) {
        try {
            var hasPrivateKey = hasText(properties.getDevicePrivateKey());
            var hasPublicKey = hasText(properties.getDevicePublicKey());
            if (hasPrivateKey != hasPublicKey) {
                throw new IllegalArgumentException(
                        "Ajan cihaz açık ve özel anahtarı birlikte yapılandırılmalıdır.");
            }
            if (hasPrivateKey) {
                this.deviceId = requireUuid(properties.getDeviceId()).toString();
                this.privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(
                        new PKCS8EncodedKeySpec(
                                Base64.getDecoder().decode(properties.getDevicePrivateKey())));
                this.publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                        new X509EncodedKeySpec(
                                Base64.getDecoder().decode(properties.getDevicePublicKey())));
                verifyKeyPair(this.privateKey, this.publicKey);
                this.persistence = "CONFIGURED";
            } else {
                var identity = loadOrCreate(
                        identityPath(properties.getLocalIdentityPath()),
                        optionalUuid(properties.getDeviceId()));
                this.deviceId = identity.deviceId().toString();
                this.privateKey = identity.keyPair().getPrivate();
                this.publicKey = identity.keyPair().getPublic();
                this.persistence = "LOCAL_FILE";
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Ajan cihaz Ed25519 anahtarı yüklenemedi.", exception);
        }
    }

    public String sign(
            String sessionId,
            String nonce,
            String certificateFingerprint,
            String signatureAlgorithm,
            String cardSignature) {
        try {
            var signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(payload(
                    deviceId,
                    sessionId,
                    nonce,
                    certificateFingerprint,
                    signatureAlgorithm,
                    cardSignature));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (Exception exception) {
            throw new AgentException(
                    "DEVICE_SIGNATURE_FAILED",
                    "Ajan cihaz kimliği imzası üretilemedi.",
                    exception);
        }
    }

    public DeviceIdentity identity() {
        return new DeviceIdentity(
                deviceId,
                "Ed25519",
                Base64.getEncoder().encodeToString(publicKey.getEncoded()),
                persistence);
    }

    public static byte[] payload(
            String deviceId,
            String sessionId,
            String nonce,
            String certificateFingerprint,
            String signatureAlgorithm,
            String cardSignature) {
        return String.join(
                        "\n",
                        deviceId,
                        sessionId,
                        nonce,
                        certificateFingerprint,
                        signatureAlgorithm,
                        cardSignature)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void verifyKeyPair(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        var challenge = "eimza-device-key-check".getBytes(StandardCharsets.UTF_8);
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(challenge);
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(challenge);
        if (!verifier.verify(signer.sign())) {
            throw new IllegalArgumentException("Ajan cihaz açık ve özel anahtarı eşleşmiyor.");
        }
    }

    private static LocalIdentity loadOrCreate(Path path, UUID configuredDeviceId) throws Exception {
        if (Files.exists(path)) {
            return load(path, configuredDeviceId);
        }
        var parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        var deviceId = configuredDeviceId == null ? UUID.randomUUID() : configuredDeviceId;
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var values = new Properties();
        values.setProperty("device-id", deviceId.toString());
        values.setProperty(
                "device-private-key",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        values.setProperty(
                "device-public-key",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        var temporary = path.resolveSibling(path.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "E-Imza Smart Card Agent local device identity");
            }
            restrictToOwner(temporary);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path);
            }
        } catch (FileAlreadyExistsException exception) {
            return load(path, configuredDeviceId);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new LocalIdentity(deviceId, pair);
    }

    private static LocalIdentity load(Path path, UUID configuredDeviceId) throws Exception {
        var values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        }
        var storedDeviceId = requireUuid(values.getProperty("device-id"));
        if (configuredDeviceId != null && !configuredDeviceId.equals(storedDeviceId)) {
            throw new IllegalArgumentException(
                    "Yapılandırılmış cihaz UUID'si yerel kimlik dosyasıyla eşleşmiyor.");
        }
        var factory = KeyFactory.getInstance("Ed25519");
        var privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(required(values, "device-private-key"))));
        var publicKey = factory.generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(required(values, "device-public-key"))));
        verifyKeyPair(privateKey, publicKey);
        return new LocalIdentity(storedDeviceId, new KeyPair(publicKey, privateKey));
    }

    private static Path identityPath(String configured) {
        if (!hasText(configured)) {
            throw new IllegalArgumentException("Yerel ajan cihaz kimliği yolu boş olamaz.");
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static UUID optionalUuid(String value) {
        return hasText(value) && !"UNCONFIGURED".equalsIgnoreCase(value.trim())
                ? requireUuid(value)
                : null;
    }

    private static UUID requireUuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Ajan cihaz kimliği geçerli bir UUID olmalıdır.", exception);
        }
    }

    private static String required(Properties values, String key) {
        var value = values.getProperty(key);
        if (!hasText(value)) {
            throw new IllegalArgumentException("Yerel cihaz kimliği eksik: " + key);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
            // Windows ve POSIX olmayan dosya sistemlerinde kullanıcı profili ACL'leri kullanılır.
        }
    }

    private record LocalIdentity(UUID deviceId, KeyPair keyPair) {}

    public record DeviceIdentity(
            String deviceId, String algorithm, String publicKey, String persistence) {}
}
