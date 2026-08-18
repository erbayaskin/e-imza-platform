package io.github.erbayaskin.eimza.api.signing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import io.github.erbayaskin.eimza.api.error.ApiException;
import io.github.erbayaskin.eimza.cades.CadesException;
import io.github.erbayaskin.eimza.cades.CadesSignatureService;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignatureLevel;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;
import io.github.erbayaskin.eimza.core.model.SigningMode;
import io.github.erbayaskin.eimza.core.model.TurkishSignatureProfile;

class SigningSessionServiceMultiSignatureTest {

    private SigningSessionRepository repository;
    private CadesSignatureService cades;
    private SigningSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(SigningSessionRepository.class);
        cades = mock(CadesSignatureService.class);
        service = new SigningSessionService(
                repository,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC),
                cades);
        when(repository.save(any(SigningSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

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

    @ParameterizedTest
    @EnumSource(value = MultiSignatureType.class, names = {"PARALLEL", "SERIAL"})
    void acceptsAdditionalAttachedCadesWithoutOriginalDocument(MultiSignatureType type) {
        var embeddedContent = "gömülü belge".getBytes(StandardCharsets.UTF_8);
        when(cades.extractAttachedContent(any(byte[].class))).thenReturn(embeddedContent);

        var response = service.create(
                UUID.randomUUID(),
                "subject",
                "attached-" + type,
                request(
                        SignatureFormat.CADES,
                        SignaturePackaging.ATTACHED,
                        type,
                        "AQ==",
                        0,
                        null,
                        null));

        assertEquals(type, response.multiSignatureType());
        verify(cades).extractAttachedContent(any(byte[].class));
        var entity = savedEntity();
        assertArrayEquals(embeddedContent, entity.documentContent());
        assertEquals(digest(embeddedContent), entity.documentDigest());
    }

    @ParameterizedTest
    @EnumSource(value = MultiSignatureType.class, names = {"PARALLEL", "SERIAL"})
    void requiresOriginalDocumentForAdditionalDetachedCades(MultiSignatureType type) {
        var error = assertThrows(ApiException.class, () -> service.create(
                UUID.randomUUID(),
                "subject",
                "detached-missing-" + type,
                request(
                        SignatureFormat.CADES,
                        SignaturePackaging.DETACHED,
                        type,
                        "AQ==",
                        0,
                        null,
                        digestRequest(new byte[] {1}))));

        assertEquals("DETACHED_CONTENT_REQUIRED", error.code());
    }

    @ParameterizedTest
    @EnumSource(value = MultiSignatureType.class, names = {"PARALLEL", "SERIAL"})
    void acceptsAdditionalDetachedCadesWithOriginalDocument(MultiSignatureType type) {
        var content = "orijinal belge".getBytes(StandardCharsets.UTF_8);

        var response = service.create(
                UUID.randomUUID(),
                "subject",
                "detached-present-" + type,
                request(
                        SignatureFormat.CADES,
                        SignaturePackaging.DETACHED,
                        type,
                        "AQ==",
                        0,
                        Base64.getEncoder().encodeToString(content),
                        digestRequest(content)));

        assertEquals(type, response.multiSignatureType());
        verify(cades).validateDetachedContent(any(byte[].class), any(byte[].class));
        assertArrayEquals(content, savedEntity().documentContent());
    }

    @Test
    void rejectsOriginalDocumentThatDiffersFromAttachedContent() {
        var embeddedContent = "gömülü belge".getBytes(StandardCharsets.UTF_8);
        var suppliedContent = "başka belge".getBytes(StandardCharsets.UTF_8);
        when(cades.extractAttachedContent(any(byte[].class))).thenReturn(embeddedContent);

        var error = assertThrows(ApiException.class, () -> service.create(
                UUID.randomUUID(),
                "subject",
                "attached-mismatch",
                request(
                        SignatureFormat.CADES,
                        SignaturePackaging.ATTACHED,
                        MultiSignatureType.SERIAL,
                        "AQ==",
                        0,
                        Base64.getEncoder().encodeToString(suppliedContent),
                        digestRequest(suppliedContent))));

        assertEquals("DOCUMENT_CONTENT_MISMATCH", error.code());
    }

    @Test
    void reportsMissingEmbeddedContentWithStableCadesCode() {
        when(cades.extractAttachedContent(any(byte[].class)))
                .thenThrow(new CadesException(
                        "CADES_ATTACHED_CONTENT_MISSING",
                        "Gömülü belge yok."));

        var error = assertThrows(ApiException.class, () -> service.create(
                UUID.randomUUID(),
                "subject",
                "attached-invalid",
                request(
                        SignatureFormat.CADES,
                        SignaturePackaging.ATTACHED,
                        MultiSignatureType.SERIAL,
                        "AQ==",
                        0,
                        null,
                        null)));

        assertEquals("CADES_ATTACHED_CONTENT_MISSING", error.code());
    }

    @Test
    void reportsDetachedContentMismatchWithStableCadesCode() {
        var content = "yanlış belge".getBytes(StandardCharsets.UTF_8);
        doThrow(new CadesException(
                        "CADES_DETACHED_CONTENT_MISMATCH",
                        "Belge eşleşmiyor."))
                .when(cades)
                .validateDetachedContent(any(byte[].class), any(byte[].class));

        var error = assertThrows(ApiException.class, () -> service.create(
                UUID.randomUUID(),
                "subject",
                "detached-mismatch",
                request(
                        SignatureFormat.CADES,
                        SignaturePackaging.DETACHED,
                        MultiSignatureType.SERIAL,
                        "AQ==",
                        0,
                        Base64.getEncoder().encodeToString(content),
                        digestRequest(content))));

        assertEquals("CADES_DETACHED_CONTENT_MISMATCH", error.code());
    }

    private SigningSessionEntity savedEntity() {
        var captor = ArgumentCaptor.forClass(SigningSessionEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static CreateSigningSessionRequest request(
            SignatureFormat format,
            SignaturePackaging packaging,
            MultiSignatureType type,
            String existingArtifact,
            int targetIndex) {
        return request(
                format,
                packaging,
                type,
                existingArtifact,
                targetIndex,
                null,
                new CreateSigningSessionRequest.DocumentDigest("SHA-256", "unused"));
    }

    private static CreateSigningSessionRequest request(
            SignatureFormat format,
            SignaturePackaging packaging,
            MultiSignatureType type,
            String existingArtifact,
            int targetIndex,
            String documentBase64,
            CreateSigningSessionRequest.DocumentDigest documentDigest) {
        return new CreateSigningSessionRequest(
                UUID.randomUUID(),
                documentDigest,
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
                documentBase64,
                packaging,
                "RSA_PKCS1_SHA256",
                type,
                existingArtifact,
                targetIndex);
    }

    private static CreateSigningSessionRequest.DocumentDigest digestRequest(byte[] content) {
        return new CreateSigningSessionRequest.DocumentDigest("SHA-256", digest(content));
    }

    private static String digest(byte[] content) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
