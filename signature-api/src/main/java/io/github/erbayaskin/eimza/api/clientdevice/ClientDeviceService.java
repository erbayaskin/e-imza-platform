package io.github.erbayaskin.eimza.api.clientdevice;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Service
public class ClientDeviceService {
    private final ClientDeviceRepository repository;
    private final Clock clock;

    public ClientDeviceService(ClientDeviceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ClientDeviceResponse register(UUID tenantId, RegisterClientDeviceRequest request) {
        var encoded = decodePublicKey(request.publicKey());
        var existing = repository.findById(request.deviceId());
        if (existing.isPresent()) {
            var value = existing.get();
            if (!value.tenantId().equals(tenantId)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "CLIENT_DEVICE_FORBIDDEN",
                        "Client cihazı başka bir tenant adına kayıtlı.",
                        false);
            }
            if (!MessageDigest.isEqual(value.publicKey(), encoded)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CLIENT_DEVICE_KEY_MISMATCH",
                        "Client cihaz UUID'si farklı bir açık anahtarla kayıtlı.",
                        false);
            }
            return ClientDeviceResponse.from(value);
        }
        var value = repository.save(ClientDeviceEntity.create(
                request.deviceId(), tenantId, request.displayName(), encoded, clock.instant()));
        return ClientDeviceResponse.from(value);
    }

    private static byte[] decodePublicKey(String value) {
        try {
            var encoded = Base64.getDecoder().decode(value);
            KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            return encoded;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CLIENT_DEVICE_PUBLIC_KEY_INVALID",
                    "Client cihaz Ed25519 açık anahtarı geçersiz.",
                    false);
        }
    }
}
