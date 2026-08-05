package io.github.erbayaskin.eimza.api.serversigning;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Component
public class ServerKeyRegistry {
    private final Map<String, ServerKeyProfile> profiles;
    private final ServerKeyProfileRepository repository;

    public ServerKeyRegistry(ServerSigningProperties properties) {
        this(properties, null);
    }

    @Autowired
    public ServerKeyRegistry(
            ServerSigningProperties properties,
            ServerKeyProfileRepository repository) {
        this.repository = repository;
        this.profiles = properties.getProfiles().stream()
                .map(ServerKeyRegistry::profile)
                .collect(Collectors.toUnmodifiableMap(
                        ServerKeyProfile::serverKeyId, Function.identity()));
    }

    public ServerKeyProfile require(String serverKeyId, UUID tenantId) {
        var profile = repository == null
                ? profiles.get(serverKeyId)
                : repository.findByServerKeyIdAndEnabledTrue(serverKeyId)
                        .map(ServerKeyRegistry::profile)
                        .orElseGet(() -> profiles.get(serverKeyId));
        if (profile == null) {
            throw error("SERVER_KEY_NOT_FOUND", "Sunucu imza anahtarı bulunamadı.");
        }
        if (!profile.allowedTenantIds().isEmpty()
                && !profile.allowedTenantIds().contains(tenantId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SERVER_KEY_FORBIDDEN",
                    "Tenant bu sunucu imza anahtarını kullanamaz.",
                    false);
        }
        return profile;
    }

    private static ServerKeyProfile profile(ServerKeyProfileEntity source) {
        var atr = decode(source.atr());
        var mask = decode(source.atrMask());
        return new ServerKeyProfile(
                source.serverKeyId(),
                source.deviceType(),
                Path.of(source.pkcs11Library()).normalize(),
                source.slotListIndex(),
                atr,
                mask,
                normalizeFingerprint(source.certificateFingerprint()),
                source.credentialRef(),
                Set.copyOf(source.allowedTenantIds()));
    }

    private static ServerKeyProfile profile(ServerSigningProperties.Profile source) {
        if (source.getServerKeyId() == null || source.getServerKeyId().isBlank()
                || source.getDeviceType() == null
                || source.getPkcs11Library() == null || source.getPkcs11Library().isBlank()) {
            throw new IllegalArgumentException(
                    "Server signing profilinde server-key-id, device-type ve pkcs11-library zorunludur.");
        }
        var configuredPath = Path.of(source.getPkcs11Library());
        if (!configuredPath.isAbsolute()) {
            throw new IllegalArgumentException(
                    "Server signing PKCS#11 kitaplığı mutlak yol olmalıdır.");
        }
        var path = configuredPath.normalize();
        if (source.getDeviceType() == ServerDeviceType.HSM) {
            if (source.getSlotListIndex() == null || source.getCredentialRef() == null
                    || source.getCredentialRef().isBlank()) {
                throw new IllegalArgumentException(
                        "HSM profilinde slot-list-index ve credential-ref zorunludur.");
            }
            if (hasText(source.getAtr()) || hasText(source.getAtrMask())) {
                throw new IllegalArgumentException("HSM profili ATR içeremez.");
            }
        } else if (!hasText(source.getAtr()) || !hasText(source.getAtrMask())) {
            throw new IllegalArgumentException(
                    "Sunucu akıllı kart profilinde atr ve atr-mask zorunludur.");
        }
        var atr = decode(source.getAtr());
        var mask = decode(source.getAtrMask());
        if (atr != null && atr.length != mask.length) {
            throw new IllegalArgumentException("ATR ve ATR maskesi aynı uzunlukta olmalıdır.");
        }
        var tenants = source.getAllowedTenantIds().stream().map(UUID::fromString).collect(Collectors.toSet());
        return new ServerKeyProfile(
                source.getServerKeyId().trim(),
                source.getDeviceType(),
                path,
                source.getSlotListIndex(),
                atr,
                mask,
                normalizeFingerprint(source.getCertificateFingerprint()),
                source.getCredentialRef(),
                tenants);
    }

    private static byte[] decode(String value) {
        return hasText(value) ? HexFormat.of().parseHex(value.replaceAll("\\s", "")) : null;
    }

    private static String normalizeFingerprint(String value) {
        return hasText(value) ? value.replaceAll("[^0-9A-Fa-f]", "").toUpperCase() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ApiException error(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message, false);
    }
}
