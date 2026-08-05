package io.github.erbayaskin.eimza.api.signing;

import java.time.Clock;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.api.error.ApiException;
import io.github.erbayaskin.eimza.core.model.SigningMode;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;

@Service
public class SigningSessionService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(5);
    private final SigningSessionRepository repository;
    private final Clock clock;

    public SigningSessionService(SigningSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SigningSessionResponse create(
            UUID tenantId,
            String subjectId,
            String idempotencyKey,
            CreateSigningSessionRequest request) {
        var mode = request.signingMode() == null ? SigningMode.CLIENT_SIDE : request.signingMode();
        validateSigningTarget(mode, request.deviceId(), request.serverKeyId());
        var packaging = packaging(request.format(), request.signaturePackaging());
        var requestedAlgorithm = normalizeAlgorithm(request.signatureAlgorithm());
        var multiSignatureType = request.multiSignatureType() == null
                ? MultiSignatureType.SINGLE
                : request.multiSignatureType();
        var targetSignatureIndex = request.targetSignatureIndex() == null
                ? 0
                : request.targetSignatureIndex();
        byte[] existingArtifact = null;
        if (request.existingArtifactBase64() != null
                && !request.existingArtifactBase64().isBlank()) {
            existingArtifact = io.github.erbayaskin.eimza.api.security.BoundedBase64Decoder.decode(
                    request.existingArtifactBase64(),
                    "existingArtifactBase64",
                    io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_SIGNATURE_BYTES);
        }
        validateMultiSignature(
                request.format(),
                packaging,
                multiSignatureType,
                existingArtifact,
                targetSignatureIndex);
        var existing =
                repository.findByTenantIdAndSubjectIdAndIdempotencyKey(
                        tenantId, subjectId, idempotencyKey);
        if (existing.isPresent()) {
            return SigningSessionResponse.from(existing.get());
        }
        if (!"SHA-256".equals(request.documentDigest().algorithm())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "ALGORITHM_NOT_ALLOWED",
                    "Faz 3 politikasında yalnız SHA-256 belge özeti kabul edilir.",
                    false);
        }
        if (request.targetLevel() != io.github.erbayaskin.eimza.core.model.SignatureLevel.B_B
                && request.targetLevel() != io.github.erbayaskin.eimza.core.model.SignatureLevel.B_T) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SESSION_LEVEL_NOT_SUPPORTED",
                    "Uçtan uca kart oturumu B-B veya B-T üretir; B-LT/B-LTA ayrı yükseltme işlemidir.",
                    false);
        }
        byte[] content = null;
        if (request.documentBase64() != null && !request.documentBase64().isBlank()) {
            content = io.github.erbayaskin.eimza.api.security.BoundedBase64Decoder.decode(
                    request.documentBase64(), "documentBase64",
                    io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_DOCUMENT_BYTES);
            try {
                var expected = decodeDigest(request.documentDigest().value());
                var actual = MessageDigest.getInstance("SHA-256").digest(content);
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "DOCUMENT_DIGEST_MISMATCH",
                            "Belge içeriği bildirilen SHA-256 özetiyle eşleşmiyor.",
                            false);
                }
            } catch (ApiException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        if (request.format() == io.github.erbayaskin.eimza.core.model.SignatureFormat.PADES
                && content == null
                && !(multiSignatureType == MultiSignatureType.SERIAL
                        && existingArtifact != null)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "PDF_CONTENT_REQUIRED",
                    "PAdES için documentBase64 alanında PDF içeriği zorunludur.",
                    false);
        }
        if (content == null
                && (packaging == SignaturePackaging.ATTACHED
                        || packaging == SignaturePackaging.ENVELOPING
                        || (request.format() == SignatureFormat.XADES
                                && packaging == SignaturePackaging.ENVELOPED)
                        || (request.format() == SignatureFormat.CADES
                                && requestedAlgorithm != null
                                && !requestedAlgorithm.endsWith("_SHA256")))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "DOCUMENT_CONTENT_REQUIRED",
                    "Seçilen paketleme veya CAdES SHA-384/SHA-512 algoritması için documentBase64 zorunludur.",
                    false);
        }
        var now = clock.instant();
        var entity =
                SigningSessionEntity.create(
                        UUID.randomUUID(),
                        tenantId,
                        subjectId,
                        mode,
                        request.deviceId(),
                        normalize(request.serverKeyId()),
                        packaging,
                        requestedAlgorithm,
                        multiSignatureType,
                        existingArtifact,
                        targetSignatureIndex,
                        request.documentId(),
                        request.format(),
                        request.targetLevel(),
                        request.turkishProfile(),
                        request.documentDigest().algorithm(),
                        request.documentDigest().value(),
                        request.documentName(),
                        request.mediaType(),
                        request.purpose(),
                        content,
                        idempotencyKey,
                        now,
                        now.plus(SESSION_TTL));
        return SigningSessionResponse.from(repository.save(entity));
    }

    private static void validateMultiSignature(
            SignatureFormat format,
            SignaturePackaging packaging,
            MultiSignatureType type,
            byte[] existingArtifact,
            int targetSignatureIndex) {
        if (type == MultiSignatureType.SINGLE) {
            if (existingArtifact != null || targetSignatureIndex != 0) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "MULTI_SIGNATURE_FIELDS_NOT_ALLOWED",
                        "SINGLE imzada existingArtifactBase64 ve targetSignatureIndex kullanılamaz.",
                        false);
            }
            return;
        }
        if (existingArtifact == null || existingArtifact.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EXISTING_SIGNATURE_REQUIRED",
                    "Paralel veya seri imza için existingArtifactBase64 zorunludur.",
                    false);
        }
        if (type == MultiSignatureType.PARALLEL && targetSignatureIndex != 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TARGET_SIGNATURE_NOT_ALLOWED",
                    "Paralel imzada targetSignatureIndex kullanılamaz.",
                    false);
        }
        if (format == SignatureFormat.PADES && type != MultiSignatureType.SERIAL) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "PADES_MULTI_SIGNATURE_NOT_ALLOWED",
                    "PAdES yalnız incremental SERIAL imzayı destekler.",
                    false);
        }
        if (format == SignatureFormat.PADES && targetSignatureIndex != 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TARGET_SIGNATURE_NOT_ALLOWED",
                    "PAdES seri imzada targetSignatureIndex yalnız 0 olabilir.",
                    false);
        }
        if (format == SignatureFormat.XADES
                && type == MultiSignatureType.PARALLEL
                && packaging == SignaturePackaging.ENVELOPED) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "XADES_PARALLEL_ENVELOPED_NOT_SUPPORTED",
                    "Paralel XAdES için DETACHED veya ENVELOPING seçilmelidir.",
                    false);
        }
    }

    private static void validateSigningTarget(
            SigningMode mode, UUID deviceId, String serverKeyId) {
        if (mode == SigningMode.CLIENT_SIDE) {
            if (deviceId == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "CLIENT_DEVICE_REQUIRED",
                        "Client-side imzalama için deviceId zorunludur.",
                        false);
            }
            if (serverKeyId != null && !serverKeyId.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SERVER_KEY_NOT_ALLOWED",
                        "Client-side imzalama serverKeyId içeremez.",
                        false);
            }
        } else {
            if (serverKeyId == null || serverKeyId.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SERVER_KEY_REQUIRED",
                        "Server-side imzalama için serverKeyId zorunludur.",
                        false);
            }
            if (deviceId != null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "CLIENT_DEVICE_NOT_ALLOWED",
                        "Server-side imzalama deviceId içeremez.",
                        false);
            }
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeAlgorithm(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            io.github.erbayaskin.eimza.cades.CadesSignatureAlgorithm.valueOf(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "ALGORITHM_NOT_ALLOWED",
                    "Desteklenen algoritmalar RSA_PKCS1_SHA256/384/512 ve ECDSA_SHA256/384/512'dir.",
                    false);
        }
    }

    private static SignaturePackaging packaging(
            SignatureFormat format, SignaturePackaging requested) {
        var value = requested == null
                ? switch (format) {
                    case CADES, XADES -> SignaturePackaging.DETACHED;
                    case PADES -> SignaturePackaging.ENVELOPED;
                }
                : requested;
        var valid = switch (format) {
            case CADES -> value == SignaturePackaging.ATTACHED
                    || value == SignaturePackaging.DETACHED;
            case XADES -> value == SignaturePackaging.ENVELOPED
                    || value == SignaturePackaging.ENVELOPING
                    || value == SignaturePackaging.DETACHED;
            case PADES -> value == SignaturePackaging.ENVELOPED;
        };
        if (!valid) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SIGNATURE_PACKAGING_NOT_ALLOWED",
                    format + " için " + value + " paketleme türü desteklenmez.",
                    false);
        }
        return value;
    }

    private static byte[] decodeDigest(String value) {
        try {
            var decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != 32) throw new IllegalArgumentException();
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DOCUMENT_DIGEST",
                    "Belge özeti 32 baytlık Base64URL SHA-256 değeri olmalıdır.",
                    false);
        }
    }

    @Transactional(readOnly = true)
    public SigningSessionResponse get(UUID tenantId, UUID sessionId) {
        return repository.findByTenantIdAndId(tenantId, sessionId)
                .map(SigningSessionResponse::from)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "SIGNING_SESSION_NOT_FOUND",
                                        "İmzalama oturumu bulunamadı.",
                                        false));
    }

    @Transactional
    public SigningSessionResponse cancel(UUID tenantId, UUID sessionId) {
        var entity =
                repository.findByTenantIdAndId(tenantId, sessionId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "SIGNING_SESSION_NOT_FOUND",
                                                "İmzalama oturumu bulunamadı.",
                                                false));
        if (entity.status().isTerminal()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SIGNING_SESSION_TERMINAL",
                    "Terminal durumdaki imzalama oturumu iptal edilemez.",
                    false);
        }
        entity.cancel(clock.instant());
        return SigningSessionResponse.from(entity);
    }
}
