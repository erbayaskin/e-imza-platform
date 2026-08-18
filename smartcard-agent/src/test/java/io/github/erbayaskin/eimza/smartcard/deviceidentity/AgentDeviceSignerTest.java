package io.github.erbayaskin.eimza.smartcard.deviceidentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;

class AgentDeviceSignerTest {
    @TempDir java.nio.file.Path temporaryDirectory;

    @Test
    void signsClientSideResultWithDeviceKey() throws Exception {
        var properties = new AgentProperties();
        properties.setDeviceId("a8a0dc09-54ab-40b7-b404-bebd55ff1756");
        properties.setLocalIdentityPath(
                temporaryDirectory.resolve("identity.properties").toString());
        var signer = new AgentDeviceSigner(properties);

        var value = signer.sign(
                "session", "nonce", "fingerprint", "RSA_PKCS1_SHA256", "card-signature");
        var identity = signer.identity();
        var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(identity.publicKey())));
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(AgentDeviceSigner.payload(
                identity.deviceId(),
                "session",
                "nonce",
                "fingerprint",
                "RSA_PKCS1_SHA256",
                "card-signature"));

        assertThat(verifier.verify(Base64.getUrlDecoder().decode(value))).isTrue();
        assertThat(identity.persistence()).isEqualTo("LOCAL_FILE");
    }

    @Test
    void generatesAndReusesUuidAndKeyPairWhenConfigurationIsEmpty() {
        var identityPath = temporaryDirectory.resolve("generated.properties").toString();
        var firstProperties = new AgentProperties();
        firstProperties.setLocalIdentityPath(identityPath);
        var first = new AgentDeviceSigner(firstProperties).identity();

        var secondProperties = new AgentProperties();
        secondProperties.setLocalIdentityPath(identityPath);
        var second = new AgentDeviceSigner(secondProperties).identity();

        assertThatCode(() -> UUID.fromString(first.deviceId())).doesNotThrowAnyException();
        assertThat(second.deviceId()).isEqualTo(first.deviceId());
        assertThat(second.publicKey()).isEqualTo(first.publicKey());
        assertThat(second.persistence()).isEqualTo("LOCAL_FILE");
    }
}