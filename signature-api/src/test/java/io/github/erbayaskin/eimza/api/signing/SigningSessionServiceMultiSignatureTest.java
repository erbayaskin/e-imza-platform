package io.github.erbayaskin.eimza.api.signing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.api.error.ApiException;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignatureLevel;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;
import io.github.erbayaskin.eimza.core.model.SigningMode;
import io.github.erbayaskin.eimza.core.model.TurkishSignatureProfile;

class SigningSessionServiceMultiSignatureTest {

    private final SigningSessionService service = new SigningSessionService(
            mock(SigningSessionRepository.class),
            Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void requiresExistingArtifactForAdditionalSignature() {
        var error = assertThrows(ApiException.class, () -> service.create(
                UUID.randomUUID(), "subject", "idem-1",
                request(SignatureFormat.CADES, SignaturePackaging.DETACHED,
                        MultiSignatureType.PARALLEL, null, 0)));

        assertEquals("EXISTING_SIGNATURE_REQUIRED", error.code());
    }

    @Test
    void rejectsParallelPades() {
        var error = assertThrows(ApiException.class, () -> service.create(
                UUID.randomUUID(), "subject", "idem-2",
                request(SignatureFormat.PADES, SignaturePackaging.ENVELOPED,
                        MultiSignatureType.PARALLEL, "AQ==", 0)));

        assertEquals("PADES_MULTI_SIGNATURE_NOT_ALLOWED", error.code());
    }

    @Test
    void rejectsParallelEnvelopedXades() {
        var error = assertThrows(ApiException.class, () -> service.create(
                UUID.randomUUID(), "subject", "idem-3",
                request(SignatureFormat.XADES, SignaturePackaging.ENVELOPED,
                        MultiSignatureType.PARALLEL, "AQ==", 0)));

        assertEquals("XADES_PARALLEL_ENVELOPED_NOT_SUPPORTED", error.code());
    }

    @Test
    void acceptsOnlyZeroTargetIndexForSequentialPades() {
        var error = assertThrows(ApiException.class, () -> service.create(
                UUID.randomUUID(), "subject", "idem-4",
                request(SignatureFormat.PADES, SignaturePackaging.ENVELOPED,
                        MultiSignatureType.SERIAL, "AQ==", 1)));

        assertEquals("TARGET_SIGNATURE_NOT_ALLOWED", error.code());
    }

    private static CreateSigningSessionRequest request(
            SignatureFormat format,
            SignaturePackaging packaging,
            MultiSignatureType type,
            String existingArtifact,
            int targetIndex) {
        return new CreateSigningSessionRequest(
                UUID.randomUUID(),
                new CreateSigningSessionRequest.DocumentDigest("SHA-256", "unused"),
                "belge.bin",
                "application/octet-stream",
                1,
                format,
                SignatureLevel.B_B,
                TurkishSignatureProfile.P1,
                "Çoklu imza testi",
                SigningMode.SERVER_SIDE,
                null,
                "server-key",
                null,
                packaging,
                "RSA_PKCS1_SHA256",
                type,
                existingArtifact,
                targetIndex);
    }
}
