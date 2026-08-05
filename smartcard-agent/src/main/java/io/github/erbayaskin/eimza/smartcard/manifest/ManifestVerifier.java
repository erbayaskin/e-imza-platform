package io.github.erbayaskin.eimza.smartcard.manifest;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

@Component
public class ManifestVerifier {

    private static final Duration MAXIMUM_LIFETIME = Duration.ofMinutes(10);
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String deviceId;
    private final PublicKey publicKey;
    private final Map<String, Instant> usedNonces = new ConcurrentHashMap<>();

    @Autowired
    public ManifestVerifier(ObjectMapper objectMapper, AgentProperties properties) {
        this(objectMapper, properties, Clock.systemUTC());
    }

    ManifestVerifier(ObjectMapper objectMapper, AgentProperties properties, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.deviceId = properties.getDeviceId();
        this.publicKey = parsePublicKey(properties.getManifestPublicKey());
    }

    public SigningManifest verify(SignedManifestRequest request) {
        try {
            var decoder = Base64.getUrlDecoder();
            var manifestBytes = decoder.decode(request.manifest());
            var signatureBytes = decoder.decode(request.signature());
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(requiredPublicKey());
            verifier.update(manifestBytes);
            if (!verifier.verify(signatureBytes)) {
                throw new AgentException("INVALID_MANIFEST_SIGNATURE", "İmzalama manifesti doğrulanamadı.");
            }
            var manifest = objectMapper.readValue(manifestBytes, SigningManifest.class);
            validate(manifest);
            return manifest;
        } catch (AgentException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new AgentException("INVALID_MANIFEST_ENCODING", "Manifest Base64URL kodlaması geçersiz.", exception);
        } catch (Exception exception) {
            throw new AgentException("INVALID_MANIFEST", "İmzalama manifesti işlenemedi.", exception);
        }
    }

    private void validate(SigningManifest manifest) {
        var now = clock.instant();
        if (!deviceId.equals(manifest.deviceId())) {
            throw new AgentException("WRONG_DEVICE", "Manifest başka bir cihaz için üretildi.");
        }
        if (!"SHA-256".equals(manifest.digestAlgorithm())) {
            throw new AgentException("UNSUPPORTED_DIGEST", "Yalnız SHA-256 özeti kabul edilir.");
        }
        if (manifest.issuedAt() == null || manifest.expiresAt() == null
                || manifest.expiresAt().isBefore(now)
                || manifest.issuedAt().isAfter(now.plusSeconds(30))
                || Duration.between(manifest.issuedAt(), manifest.expiresAt()).compareTo(MAXIMUM_LIFETIME) > 0) {
            throw new AgentException("MANIFEST_EXPIRED", "Manifest süresi geçersiz veya dolmuş.");
        }
        if (manifest.nonce() == null || manifest.nonce().isBlank()) {
            throw new AgentException("INVALID_NONCE", "Manifest nonce alanı zorunludur.");
        }
        usedNonces.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        if (usedNonces.putIfAbsent(manifest.nonce(), manifest.expiresAt()) != null) {
            throw new AgentException("MANIFEST_REPLAYED", "Manifest daha önce kullanılmış.");
        }
    }

    private PublicKey requiredPublicKey() {
        if (deviceId == null || deviceId.isBlank() || "UNCONFIGURED".equals(deviceId)) {
            throw new AgentException(
                    "DEVICE_NOT_CONFIGURED",
                    "Yerel aracı cihaz kimliği yapılandırılmamış; imzalama kapalı.");
        }
        if (publicKey == null) {
            throw new AgentException(
                    "MANIFEST_KEY_NOT_CONFIGURED",
                    "Merkezi manifest doğrulama anahtarı yapılandırılmamış; imzalama kapalı.");
        }
        return publicKey;
    }

    private static PublicKey parsePublicKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            var normalized = configured
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            var encoded = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Manifest Ed25519 açık anahtarı geçersiz.", exception);
        }
    }
}
