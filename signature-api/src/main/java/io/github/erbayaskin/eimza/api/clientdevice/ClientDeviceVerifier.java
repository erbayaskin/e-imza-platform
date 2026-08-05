package io.github.erbayaskin.eimza.api.clientdevice;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Component
public class ClientDeviceVerifier {
    private final ClientDeviceRepository repository;

    public ClientDeviceVerifier(ClientDeviceRepository repository) {
        this.repository = repository;
    }

    public void verify(
            UUID tenantId,
            UUID deviceId,
            UUID sessionId,
            String nonce,
            String certificateFingerprint,
            String signatureAlgorithm,
            String cardSignature,
            String deviceSignature) {
        var device = repository.findByTenantIdAndDeviceId(tenantId, deviceId)
                .filter(ClientDeviceEntity::enabled)
                .orElseThrow(() -> error(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "CLIENT_DEVICE_NOT_REGISTERED",
                        "Client cihazı tenant için kayıtlı ve etkin değil."));
        try {
            var key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(device.publicKey()));
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(payload(
                    deviceId.toString(),
                    sessionId.toString(),
                    nonce,
                    certificateFingerprint,
                    signatureAlgorithm,
                    cardSignature));
            if (!verifier.verify(Base64.getUrlDecoder().decode(deviceSignature))) {
                throw error(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "CLIENT_DEVICE_SIGNATURE_INVALID",
                        "Client cihaz kimliği imzası geçersiz.");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "CLIENT_DEVICE_SIGNATURE_INVALID",
                    "Client cihaz kimliği imzası doğrulanamadı.");
        }
    }

    private static byte[] payload(
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

    private static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message, false);
    }
}
