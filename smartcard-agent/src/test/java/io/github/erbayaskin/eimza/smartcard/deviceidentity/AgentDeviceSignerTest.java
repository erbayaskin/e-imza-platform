package io.github.erbayaskin.eimza.smartcard.deviceidentity;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;

class AgentDeviceSignerTest {
    @Test
    void signsClientSideResultWithDeviceKey() throws Exception {
        var properties = new AgentProperties();
        properties.setDeviceId("a8a0dc09-54ab-40b7-b404-bebd55ff1756");
        var signer = new AgentDeviceSigner(properties);

        var value = signer.sign("session", "nonce", "fingerprint", "RSA_PKCS1_SHA256", "card-signature");
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
        assertThat(identity.persistence()).isEqualTo("EPHEMERAL");
    }
}
