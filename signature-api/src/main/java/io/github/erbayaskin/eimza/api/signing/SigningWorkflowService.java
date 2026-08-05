package io.github.erbayaskin.eimza.api.signing;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import io.github.erbayaskin.eimza.api.error.ApiException;
import io.github.erbayaskin.eimza.api.longterm.TimestampProperties;
import io.github.erbayaskin.eimza.api.longterm.TsaProfileService;
import io.github.erbayaskin.eimza.api.clientdevice.ClientDeviceVerifier;
import io.github.erbayaskin.eimza.api.serversigning.ServerKeyRegistry;
import io.github.erbayaskin.eimza.api.serversigning.ServerPkcs11SigningService;
import io.github.erbayaskin.eimza.api.validation.CertificateValidationApiRequest;
import io.github.erbayaskin.eimza.api.validation.ValidationService;
import io.github.erbayaskin.eimza.api.validation.policy.ValidationPolicyConfigurationService;
import io.github.erbayaskin.eimza.cades.CadesSignatureAlgorithm;
import io.github.erbayaskin.eimza.cades.CadesSignatureService;
import io.github.erbayaskin.eimza.cades.CadesSigningPreparation;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignatureLevel;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;
import io.github.erbayaskin.eimza.core.model.SigningMode;
import io.github.erbayaskin.eimza.core.model.ValidationIndication;
import io.github.erbayaskin.eimza.core.signing.SigningSessionStatus;
import io.github.erbayaskin.eimza.pades.PadesSignatureService;
import io.github.erbayaskin.eimza.pades.PadesSigningPreparation;
import io.github.erbayaskin.eimza.timestamp.TimestampClient;
import io.github.erbayaskin.eimza.xades.XadesSignatureService;
import io.github.erbayaskin.eimza.xades.XadesSigningPreparation;

@Service
public class SigningWorkflowService {
    private final SigningSessionRepository sessions;
    private final SigningPreparationRepository preparations;
    private final SignatureArtifactRepository artifacts;
    private final ManifestSigner manifestSigner;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TimestampClient timestampClient;
    private final TimestampProperties timestampProperties;
    private final TsaProfileService tsaProfiles;
    private final ValidationService validationService;
    private final ValidationPolicyConfigurationService policyConfigurations;
    private final ServerKeyRegistry serverKeys;
    private final ServerPkcs11SigningService serverSigning;
    private final ClientDeviceVerifier clientDevices;
    private final CadesSignatureService cades = new CadesSignatureService();
    private final XadesSignatureService xades = new XadesSignatureService();
    private final PadesSignatureService pades = new PadesSignatureService();

    public SigningWorkflowService(
            SigningSessionRepository sessions,
            SigningPreparationRepository preparations,
            SignatureArtifactRepository artifacts,
            ManifestSigner manifestSigner,
            ObjectMapper objectMapper,
            Clock clock,
            ObjectProvider<TimestampClient> timestampClient,
            TimestampProperties timestampProperties,
            TsaProfileService tsaProfiles,
            ValidationService validationService,
            ValidationPolicyConfigurationService policyConfigurations,
            ServerKeyRegistry serverKeys,
            ServerPkcs11SigningService serverSigning,
            ClientDeviceVerifier clientDevices) {
        this.sessions = sessions;
        this.preparations = preparations;
        this.artifacts = artifacts;
        this.manifestSigner = manifestSigner;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.timestampClient = timestampClient.getIfAvailable();
        this.timestampProperties = timestampProperties;
        this.tsaProfiles = tsaProfiles;
        this.validationService = validationService;
        this.policyConfigurations = policyConfigurations;
        this.serverKeys = serverKeys;
        this.serverSigning = serverSigning;
        this.clientDevices = clientDevices;
    }

    @Transactional
    public SigningManifestResponse prepareManifest(
            UUID tenantId, UUID sessionId, PrepareSigningManifestRequest request) {
        var session = session(tenantId, sessionId);
        requireMode(session, SigningMode.CLIENT_SIDE);
        requireStatus(session, SigningSessionStatus.CREATED);
        if (session.expiresAt().isBefore(clock.instant())) {
            session.transition(SigningSessionStatus.EXPIRED, clock.instant());
            throw error(HttpStatus.GONE, "SIGNING_SESSION_EXPIRED", "İmzalama oturumunun süresi doldu.");
        }
        try {
            var certBytes = Base64.getDecoder().decode(request.certificateBase64());
            var certificate = certificate(certBytes);
            var fingerprint = HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certBytes));
            if (!fingerprint.equalsIgnoreCase(request.certificateFingerprint())) {
                throw error(HttpStatus.UNPROCESSABLE_CONTENT, "CERTIFICATE_FINGERPRINT_MISMATCH",
                        "Sertifika parmak izi sertifika içeriğiyle eşleşmiyor.");
            }
            var effectiveAlgorithm = effectiveAlgorithm(
                    session, request.signatureAlgorithm());
            var prepared = prepare(session, certificate, effectiveAlgorithm);
            var nonce = UUID.randomUUID().toString();
            var now = clock.instant();
            var expires = min(session.expiresAt(), now.plus(Duration.ofMinutes(2)));
            var manifest = new SigningManifest(
                    session.id().toString(), session.deviceId().toString(), request.readerId(),
                    fingerprint, "SHA-256",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(prepared.digestToSign()),
                    effectiveAlgorithm, nonce, now, expires);
            var manifestBytes = objectMapper.writeValueAsBytes(manifest);
            var manifestSignature = manifestSigner.sign(manifestBytes);
            preparations.save(SigningPreparationEntity.create(
                    session.id(), session.format().name(), prepared.json(), fingerprint, certBytes,
                    effectiveAlgorithm, request.readerId(), nonce,
                    new String(manifestBytes, StandardCharsets.UTF_8), manifestSignature, now));
            session.transition(SigningSessionStatus.MANIFEST_ISSUED, now);
            return new SigningManifestResponse(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(manifestBytes),
                    manifestSignature, manifestSigner.keyId(), manifestSigner.publicKey(), expires);
        } catch (ApiException exception) {
            throw exception;
        } catch (io.github.erbayaskin.eimza.cades.CadesException exception) {
            throw error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    exception.code(),
                    exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_CERTIFICATE_ENCODING",
                    "Sertifika Base64 kodlaması geçersiz.");
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "SIGNING_PREPARATION_FAILED",
                    "İmzalanacak veri hazırlanamadı: " + exception.getMessage(), false);
        }
    }

    private Prepared prepare(
            SigningSessionEntity session, X509Certificate certificate, String algorithm) throws Exception {
        var selected = algorithm(algorithm);
        var certificateValidityCheckActive =
                policyConfigurations.current().signingCertificateValidityActive();
        return switch (session.format()) {
            case CADES -> {
                CadesSigningPreparation value;
                if (session.multiSignatureType()
                        == io.github.erbayaskin.eimza.core.model.MultiSignatureType.SERIAL) {
                    value = cades.prepareCounterSignature(
                            session.existingArtifact(),
                            session.targetSignatureIndex(),
                            certificate,
                            selected,
                            null,
                            clock.instant(),
                            certificateValidityCheckActive);
                } else if (session.documentContent() != null) {
                    value = session.multiSignatureType()
                                    == io.github.erbayaskin.eimza.core.model.MultiSignatureType.PARALLEL
                            ? cades.prepareParallel(
                                    session.existingArtifact(),
                                    session.documentContent(),
                                    certificate,
                                    selected,
                                    null,
                                    clock.instant(),
                                    session.signaturePackaging()
                                            == SignaturePackaging.ATTACHED,
                                    certificateValidityCheckActive)
                            : cades.prepare(
                                    session.documentContent(),
                                    certificate,
                                    selected,
                                    null,
                                    clock.instant(),
                                    session.signaturePackaging()
                                            == SignaturePackaging.ATTACHED,
                                    certificateValidityCheckActive);
                } else if (selected.digestName().equals("SHA-256")
                        && session.signaturePackaging() == SignaturePackaging.DETACHED) {
                    value = session.multiSignatureType()
                                    == io.github.erbayaskin.eimza.core.model.MultiSignatureType.PARALLEL
                            ? cades.prepareParallelDigest(
                                    session.existingArtifact(),
                                    decodeUrl(session.documentDigest()),
                                    certificate,
                                    selected,
                                    null,
                                    clock.instant(),
                                    certificateValidityCheckActive)
                            : cades.prepareDigest(
                                    decodeUrl(session.documentDigest()),
                                    certificate,
                                    selected,
                                    null,
                                    clock.instant(),
                                    certificateValidityCheckActive);
                } else {
                    throw error(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "DOCUMENT_CONTENT_REQUIRED",
                            "Attached veya SHA-384/SHA-512 CAdES için documentBase64 zorunludur.");
                }
                yield new Prepared(value.digestToSign(), objectMapper.writeValueAsString(value));
            }
            case XADES -> {
                XadesSigningPreparation value;
                if (session.multiSignatureType()
                        == io.github.erbayaskin.eimza.core.model.MultiSignatureType.SERIAL) {
                    value = xades.prepareCounterSignature(
                            session.existingArtifact(),
                            session.targetSignatureIndex(),
                            certificate,
                            algorithm,
                            clock.instant(),
                            certificateValidityCheckActive);
                } else if (session.multiSignatureType()
                        == io.github.erbayaskin.eimza.core.model.MultiSignatureType.PARALLEL) {
                    value = xades.prepareParallel(
                            session.existingArtifact(),
                            session.signaturePackaging(),
                            "urn:uuid:" + session.documentId(),
                            session.documentContent(),
                            session.mediaType(),
                            decodeUrl(session.documentDigest()),
                            certificate,
                            algorithm,
                            clock.instant(),
                            certificateValidityCheckActive);
                } else {
                    value = xades.prepare(
                            session.signaturePackaging(),
                            "urn:uuid:" + session.documentId(),
                            session.documentContent(),
                            session.mediaType(),
                            decodeUrl(session.documentDigest()),
                            certificate,
                            algorithm,
                            clock.instant(),
                            certificateValidityCheckActive);
                }
                yield new Prepared(value.digestToSign(), objectMapper.writeValueAsString(value));
            }
            case PADES -> {
                var value = session.multiSignatureType()
                                == io.github.erbayaskin.eimza.core.model.MultiSignatureType.SERIAL
                        ? pades.prepareSequential(
                                session.existingArtifact(),
                                certificate,
                                selected,
                                null,
                                clock.instant(),
                                certificate.getSubjectX500Principal().getName(),
                                session.purpose(),
                                certificateValidityCheckActive)
                        : pades.prepare(
                                session.documentContent(),
                                certificate,
                                selected,
                                null,
                                clock.instant(),
                                certificate.getSubjectX500Principal().getName(),
                                session.purpose(),
                                certificateValidityCheckActive);
                yield new Prepared(
                        value.cmsPreparation().digestToSign(),
                        objectMapper.writeValueAsString(value));
            }
        };
    }

    @Transactional
    public SigningSessionResponse agentConnected(UUID tenantId, UUID sessionId) {
        var value = session(tenantId, sessionId);
        requireMode(value, SigningMode.CLIENT_SIDE);
        requireStatus(value, SigningSessionStatus.MANIFEST_ISSUED);
        value.transition(SigningSessionStatus.AGENT_CONNECTED, clock.instant());
        value.transition(SigningSessionStatus.USER_APPROVAL_PENDING, clock.instant());
        return SigningSessionResponse.from(value);
    }

    @Transactional
    public SigningSessionResponse approve(UUID tenantId, UUID sessionId) {
        var value = session(tenantId, sessionId);
        requireMode(value, SigningMode.CLIENT_SIDE);
        requireStatus(value, SigningSessionStatus.USER_APPROVAL_PENDING);
        value.transition(SigningSessionStatus.CARD_SIGNING, clock.instant());
        return SigningSessionResponse.from(value);
    }

    @Transactional
    public SignatureArtifactResponse complete(
            UUID tenantId, UUID sessionId, CompleteSigningSessionRequest request) {
        var session = session(tenantId, sessionId);
        requireMode(session, SigningMode.CLIENT_SIDE);
        var existing = artifacts.findBySessionId(sessionId);
        if (existing.isPresent()) return SignatureArtifactResponse.from(existing.get());
        requireStatus(session, SigningSessionStatus.CARD_SIGNING);
        var preparation = preparations.findById(sessionId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "SIGNING_PREPARATION_NOT_FOUND",
                        "İmzalama hazırlığı bulunamadı."));
        try {
            clientDevices.verify(
                    tenantId,
                    session.deviceId(),
                    session.id(),
                    preparation.nonce(),
                    preparation.certificateFingerprint(),
                    preparation.signatureAlgorithm(),
                    request.signature(),
                    request.deviceSignature());
            var rawSignature = Base64.getUrlDecoder().decode(request.signature());
            return finalizeSignature(session, preparation, rawSignature);
        } catch (ApiException exception) {
            failIfPossible(session);
            throw exception;
        } catch (Exception exception) {
            failIfPossible(session);
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "SIGNATURE_COMPLETION_FAILED",
                    "İmza tamamlanamadı: " + exception.getMessage(), false);
        }
    }

    @Transactional
    public SignatureArtifactResponse serverSign(
            UUID tenantId, UUID sessionId, ServerSideSigningRequest request) {
        var session = session(tenantId, sessionId);
        var existing = artifacts.findBySessionId(sessionId);
        if (existing.isPresent()) return SignatureArtifactResponse.from(existing.get());
        requireMode(session, SigningMode.SERVER_SIDE);
        requireStatus(session, SigningSessionStatus.CREATED);
        if (session.expiresAt().isBefore(clock.instant())) {
            session.transition(SigningSessionStatus.EXPIRED, clock.instant());
            throw error(HttpStatus.GONE, "SIGNING_SESSION_EXPIRED", "İmzalama oturumunun süresi doldu.");
        }
        try {
            var profile = serverKeys.require(session.serverKeyId(), tenantId);
            session.transition(SigningSessionStatus.CARD_SIGNING, clock.instant());
            var signed = serverSigning.sign(
                    profile,
                    request.pin(),
                    session.requestedSignatureAlgorithm(),
                    (certificate, algorithm) -> {
                        var value = prepare(session, certificate, algorithm);
                        return new ServerPkcs11SigningService.PreparedPayload(
                                value.digestToSign(), value.json());
                    });
            var now = clock.instant();
            var preparation = preparations.save(SigningPreparationEntity.create(
                    session.id(),
                    session.format().name(),
                    signed.preparationJson(),
                    signed.certificateFingerprint(),
                    signed.certificate(),
                    signed.signatureAlgorithm(),
                    "SERVER_SIDE:" + profile.serverKeyId(),
                    "SERVER_SIDE:" + session.id(),
                    "{}",
                    "SERVER_SIDE",
                    now));
            return finalizeSignature(session, preparation, signed.rawSignature());
        } catch (ApiException exception) {
            failIfPossible(session);
            throw exception;
        } catch (Exception exception) {
            failIfPossible(session);
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SERVER_SIGNING_FAILED",
                    "Server-side imzalama tamamlanamadı.",
                    false);
        }
    }

    private SignatureArtifactResponse finalizeSignature(
            SigningSessionEntity session,
            SigningPreparationEntity preparation,
            byte[] rawSignature) throws Exception {
        session.transition(SigningSessionStatus.SIGNATURE_RECEIVED, clock.instant());
        session.transition(SigningSessionStatus.VALIDATING, clock.instant());
        validateCertificate(preparation);
        session.transition(SigningSessionStatus.TIMESTAMPING, clock.instant());
        var encoded = completeFormat(session, preparation, rawSignature);
        var digest = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(encoded));
        var artifact = artifacts.save(SignatureArtifactEntity.create(
                session.id(), digest, session.format(), session.targetLevel(), null,
                preparation.certificateFingerprint(), encoded, mediaType(session.format()), clock.instant()));
        session.transition(SigningSessionStatus.COMPLETED, clock.instant());
        return SignatureArtifactResponse.from(artifact);
    }

    private void validateCertificate(SigningPreparationEntity preparation) {
        if (!policyConfigurations.current().signingCertificateValidityActive()) {
            return;
        }
        var report = validationService.validateCertificate(new CertificateValidationApiRequest(
                Base64.getEncoder().encodeToString(preparation.certificate()), List.of(), clock.instant()));
        if (report.mainIndication() != ValidationIndication.VALID) {
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "SIGNING_CERTIFICATE_NOT_TRUSTED",
                    "İmzalayan sertifika güvenilir kök/alt kök deposuna göre geçerli değil: "
                            + report.mainIndication());
        }
    }

    private byte[] completeFormat(
            SigningSessionEntity session, SigningPreparationEntity preparation, byte[] rawSignature)
            throws Exception {
        var timestamp = session.targetLevel() == SignatureLevel.B_T;
        if (timestamp && timestampClient == null) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "TSA_NOT_CONFIGURED",
                    "B-T üretimi için zaman damgası servisi yapılandırılmalıdır.");
        }
        return switch (session.format()) {
            case CADES -> {
                var value = objectMapper.readValue(
                        preparation.preparationJson(), CadesSigningPreparation.class);
                yield timestamp
                        ? cades.completeWithTimestamp(value, rawSignature, timestampClient,
                                timestampPolicyOid()).encodedSignature()
                        : cades.completeBaseline(value, rawSignature).encodedSignature();
            }
            case XADES -> {
                var value = objectMapper.readValue(
                        preparation.preparationJson(), XadesSigningPreparation.class);
                yield timestamp
                        ? xades.completeWithTimestamp(value, rawSignature, timestampClient,
                                timestampPolicyOid()).encodedSignature()
                        : xades.completeBaseline(value, rawSignature).encodedSignature();
            }
            case PADES -> {
                var value = objectMapper.readValue(
                        preparation.preparationJson(), PadesSigningPreparation.class);
                yield timestamp
                        ? pades.completeWithTimestamp(value, rawSignature, timestampClient,
                                timestampPolicyOid()).encodedPdf()
                        : pades.completeBaseline(value, rawSignature).encodedPdf();
            }
        };
    }

    private String timestampPolicyOid() {
        var configured = tsaProfiles.current().archivePolicyOid();
        return configured == null || configured.isBlank()
                ? timestampProperties.getArchivePolicyOid()
                : configured;
    }

    @Transactional(readOnly = true)
    public SignatureArtifactResponse artifact(UUID tenantId, UUID sessionId) {
        session(tenantId, sessionId);
        return artifacts.findBySessionId(sessionId).map(SignatureArtifactResponse::from)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SIGNATURE_ARTIFACT_NOT_FOUND",
                        "İmza çıktısı bulunamadı."));
    }

    private SigningSessionEntity session(UUID tenantId, UUID sessionId) {
        return sessions.findByTenantIdAndId(tenantId, sessionId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SIGNING_SESSION_NOT_FOUND",
                        "İmzalama oturumu bulunamadı."));
    }

    private static void requireStatus(SigningSessionEntity value, SigningSessionStatus expected) {
        if (value.status() != expected) {
            throw error(HttpStatus.CONFLICT, "INVALID_SIGNING_SESSION_STATUS",
                    "Beklenen durum " + expected + ", mevcut durum " + value.status() + ".");
        }
    }

    private static void requireMode(SigningSessionEntity session, SigningMode expected) {
        if (session.signingMode() != expected) {
            throw error(
                    HttpStatus.CONFLICT,
                    "SIGNING_MODE_MISMATCH",
                    "Bu işlem yalnız " + expected + " imzalama oturumlarında kullanılabilir.");
        }
    }

    private static void failIfPossible(SigningSessionEntity session) {
        if (io.github.erbayaskin.eimza.core.signing.SigningSessionStateMachine.canTransition(
                session.status(), SigningSessionStatus.FAILED)) {
            session.transition(SigningSessionStatus.FAILED, java.time.Instant.now());
        }
    }

    private static byte[] decodeUrl(String value) { return Base64.getUrlDecoder().decode(value); }
    private static CadesSignatureAlgorithm algorithm(String value) {
        try { return CadesSignatureAlgorithm.valueOf(value); }
        catch (IllegalArgumentException exception) {
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ALGORITHM_NOT_ALLOWED",
                    "Desteklenen algoritmalar RSA_PKCS1_SHA256/384/512 ve ECDSA_SHA256/384/512'dir.");
        }
    }
    private static String effectiveAlgorithm(
            SigningSessionEntity session, String suppliedAlgorithm) {
        var requested = session.requestedSignatureAlgorithm();
        if (requested != null && !requested.equalsIgnoreCase(suppliedAlgorithm)) {
            throw error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SIGNATURE_ALGORITHM_MISMATCH",
                    "Manifest algoritması oturumda seçilen algoritmayla eşleşmiyor.");
        }
        return requested == null
                ? algorithm(suppliedAlgorithm).name()
                : algorithm(requested).name();
    }
    private static X509Certificate certificate(byte[] encoded) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }
    private static java.time.Instant min(java.time.Instant left, java.time.Instant right) {
        return left.isBefore(right) ? left : right;
    }
    private static String mediaType(SignatureFormat format) {
        return switch (format) {
            case CADES -> "application/pkcs7-signature";
            case XADES -> "application/xml";
            case PADES -> "application/pdf";
        };
    }
    private static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message, false);
    }

    private record SigningManifest(
            String sessionId, String deviceId, String readerId, String certificateFingerprint,
            String digestAlgorithm, String digest, String signatureAlgorithm, String nonce,
            java.time.Instant issuedAt, java.time.Instant expiresAt) {}

    private record Prepared(byte[] digestToSign, String json) {
        private Prepared {
            digestToSign = digestToSign.clone();
        }
        @Override public byte[] digestToSign() { return digestToSign.clone(); }
    }
}
