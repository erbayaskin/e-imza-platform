package io.github.erbayaskin.eimza.smartcard.deviceidentity;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

@Component
public class AgentDeviceSigner {
    private final String deviceId;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final boolean ephemeral;

    public AgentDeviceSigner(AgentProperties properties) {
        this.deviceId = properties.getDeviceId();
        try {
            if (properties.getDevicePrivateKey() == null
                    || properties.getDevicePrivateKey().isBlank()) {
                var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
                this.ephemeral = true;
            } else if (properties.getDevicePublicKey() != null
                    && !properties.getDevicePublicKey().isBlank()) {
                this.privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(
                        new PKCS8EncodedKeySpec(
                                Base64.getDecoder().decode(properties.getDevicePrivateKey())));
                this.publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                        new X509EncodedKeySpec(
                                Base64.getDecoder().decode(properties.getDevicePublicKey())));
                this.ephemeral = false;
                verifyKeyPair(this.privateKey, this.publicKey);
            } else {
                throw new IllegalArgumentException(
                        "Yapılandırılmış cihaz özel anahtarıyla birlikte açık anahtar da zorunludur.");
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
                ephemeral ? "EPHEMERAL" : "CONFIGURED");
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

    public record DeviceIdentity(
            String deviceId, String algorithm, String publicKey, String persistence) {}
}
