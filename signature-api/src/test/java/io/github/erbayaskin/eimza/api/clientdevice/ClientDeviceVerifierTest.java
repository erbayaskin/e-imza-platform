package io.github.erbayaskin.eimza.api.clientdevice;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientDeviceVerifierTest {
    @Test
    void verifiesDeviceBoundClientSideResult() throws Exception {
        var tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var deviceId = UUID.fromString("a8a0dc09-54ab-40b7-b404-bebd55ff1756");
        var sessionId = UUID.randomUUID();
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var repository = mock(ClientDeviceRepository.class);
        when(repository.findByTenantIdAndDeviceId(tenantId, deviceId))
                .thenReturn(Optional.of(ClientDeviceEntity.create(
                        deviceId, tenantId, "test", keyPair.getPublic().getEncoded(), Instant.now())));
        var verifier = new ClientDeviceVerifier(repository);
        var payload = String.join(
                        "\n",
                        deviceId.toString(),
                        sessionId.toString(),
                        "nonce",
                        "fingerprint",
                        "RSA_PKCS1_SHA256",
                        "card-signature")
                .getBytes(StandardCharsets.UTF_8);
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload);
        var deviceSignature =
                Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());

        assertThatCode(() -> verifier.verify(
                        tenantId,
                        deviceId,
                        sessionId,
                        "nonce",
                        "fingerprint",
                        "RSA_PKCS1_SHA256",
                        "card-signature",
                        deviceSignature))
                .doesNotThrowAnyException();
    }
}
