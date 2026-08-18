package io.github.erbayaskin.eimza.smartcard.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;
import tools.jackson.databind.json.JsonMapper;

class ManifestVerifierTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");
    private KeyPair keyPair;
    private ManifestVerifier verifier;
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        var properties = new AgentProperties();
        properties.setDeviceId("cihaz-1");
        properties.setManifestPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        verifier = new ManifestVerifier(
                objectMapper, properties, "cihaz-1", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void verifiesExactSignedBytesAndRejectsReplay() throws Exception {
        var request = signedRequest(manifest("nonce-1", NOW.minusSeconds(5), NOW.plusSeconds(60)));

        assertThat(verifier.verify(request).sessionId()).isEqualTo("oturum-1");
        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOfSatisfying(
                        AgentException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MANIFEST_REPLAYED"));
    }

    @Test
    void rejectsExpiredManifest() throws Exception {
        var request = signedRequest(manifest("nonce-2", NOW.minusSeconds(120), NOW.minusSeconds(1)));

        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOfSatisfying(
                        AgentException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MANIFEST_EXPIRED"));
    }

    @Test
    void rejectsTamperedManifest() throws Exception {
        var request = signedRequest(manifest("nonce-3", NOW.minusSeconds(5), NOW.plusSeconds(60)));
        var decoded = Base64.getUrlDecoder().decode(request.manifest());
        decoded[decoded.length - 2] ^= 1;
        var tampered = new SignedManifestRequest(
                Base64.getUrlEncoder().withoutPadding().encodeToString(decoded), request.signature());

        assertThatThrownBy(() -> verifier.verify(tampered))
                .isInstanceOfSatisfying(
                        AgentException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_MANIFEST_SIGNATURE"));
    }

    private SigningManifest manifest(String nonce, Instant issuedAt, Instant expiresAt) {
        return new SigningManifest(
                "oturum-1",
                "cihaz-1",
                "okuyucu-1",
                "AA",
                "SHA-256",
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]),
                "RSA_PKCS1_SHA256",
                nonce,
                issuedAt,
                expiresAt);
    }

    private SignedManifestRequest signedRequest(SigningManifest manifest) throws Exception {
        var bytes = objectMapper.writeValueAsBytes(manifest);
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(bytes);
        return new SignedManifestRequest(
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
    }
}
