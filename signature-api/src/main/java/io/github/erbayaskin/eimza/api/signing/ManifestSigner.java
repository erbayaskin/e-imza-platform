package io.github.erbayaskin.eimza.api.signing;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class ManifestSigner {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;

    ManifestSigner(ManifestSigningProperties properties, Environment environment) {
        try {
            if (!properties.getPrivateKey().isBlank() && !properties.getPublicKey().isBlank()) {
                var factory = KeyFactory.getInstance("Ed25519");
                privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(
                        decodePem(properties.getPrivateKey())));
                publicKey = factory.generatePublic(new X509EncodedKeySpec(
                        decodePem(properties.getPublicKey())));
                this.keyId = properties.getKeyId();
            } else {
                if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
                    throw new IllegalStateException(
                            "Üretim profilinde EIMZA_MANIFEST_PRIVATE_KEY ve PUBLIC_KEY zorunludur.");
                }
                KeyPair pair = loadOrCreateLocalKeyPair(properties.getLocalKeyPath());
                privateKey = pair.getPrivate();
                publicKey = pair.getPublic();
                this.keyId = "local-" + HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()),
                        0,
                        8);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Manifest Ed25519 anahtarları yüklenemedi.", exception);
        }
    }

    String sign(byte[] content) {
        try {
            var signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(content);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("Manifest imzalanamadı.", exception);
        }
    }

    String publicKey() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    String keyId() { return keyId; }

    private static KeyPair loadOrCreateLocalKeyPair(String configuredPath) throws Exception {
        var base = Path.of(configuredPath).toAbsolutePath().normalize();
        var privatePath = Path.of(base + ".pk8");
        var publicPath = Path.of(base + ".spki");
        var privateExists = Files.isRegularFile(privatePath);
        var publicExists = Files.isRegularFile(publicPath);
        var factory = KeyFactory.getInstance("Ed25519");
        if (privateExists && publicExists) {
            return new KeyPair(
                    factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicPath))),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privatePath))));
        }
        if (privateExists || publicExists) {
            throw new IllegalStateException(
                    "Yerel manifest anahtar çiftinin yalnız bir dosyası mevcut: " + base);
        }
        var parent = base.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.write(privatePath, pair.getPrivate().getEncoded());
        Files.write(publicPath, pair.getPublic().getEncoded());
        return pair;
    }

    private static byte[] decodePem(String value) {
        return Base64.getDecoder().decode(value
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", ""));
    }
}
