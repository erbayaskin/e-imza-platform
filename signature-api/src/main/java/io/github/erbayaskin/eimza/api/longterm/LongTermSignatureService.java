package io.github.erbayaskin.eimza.api.longterm;

import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.api.error.ApiException;
import io.github.erbayaskin.eimza.api.security.ApiLimits;
import io.github.erbayaskin.eimza.api.security.BoundedBase64Decoder;
import io.github.erbayaskin.eimza.cades.CadesAugmentationResult;
import io.github.erbayaskin.eimza.cades.CadesException;
import io.github.erbayaskin.eimza.cades.CadesLongTermService;
import io.github.erbayaskin.eimza.cades.CadesRevocationValue;
import io.github.erbayaskin.eimza.cades.CadesValidationMaterial;
import io.github.erbayaskin.eimza.timestamp.TimestampClient;

@Service
public class LongTermSignatureService {

    private final Optional<TimestampClient> timestampClient;
    private final TimestampProperties timestampProperties;
    private final TsaProfileService tsaProfiles;
    private final SignatureAugmentationRepository repository;
    private final Clock clock;
    private final CadesLongTermService service = new CadesLongTermService();

    public LongTermSignatureService(
            Optional<TimestampClient> timestampClient,
            TimestampProperties timestampProperties,
            SignatureAugmentationRepository repository,
            Clock clock) {
        this(timestampClient, timestampProperties, repository, clock, null);
    }

    @Autowired
    public LongTermSignatureService(
            Optional<TimestampClient> timestampClient,
            TimestampProperties timestampProperties,
            SignatureAugmentationRepository repository,
            Clock clock,
            TsaProfileService tsaProfiles) {
        this.timestampClient = timestampClient;
        this.timestampProperties = timestampProperties;
        this.repository = repository;
        this.clock = clock;
        this.tsaProfiles = tsaProfiles;
    }

    @Transactional
    public LongTermAugmentationResponse augment(LongTermAugmentationRequest request) {
        var source = BoundedBase64Decoder.decode(
                request.signature(), "signature", ApiLimits.MAX_SIGNATURE_BYTES);
        var content = request.content() == null || request.content().isBlank()
                ? null
                : BoundedBase64Decoder.decode(
                        request.content(), "content", ApiLimits.MAX_DOCUMENT_BYTES);
        try {
            CadesAugmentationResult result;
            if (request.target() == LongTermTarget.B_LT) {
                result = service.augmentToBaselineLT(source, material(request));
            } else {
                requireContent(content);
                var tsa = timestampClient.orElseThrow(() -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "TSA_NOT_CONFIGURED",
                        "B-LTA işlemi için TSA yapılandırılmamış.",
                        true));
                var policy = request.timestampPolicyOid() == null
                                || request.timestampPolicyOid().isBlank()
                        ? timestampPolicyOid()
                        : request.timestampPolicyOid();
                if (request.target() == LongTermTarget.RENEW_B_LTA) {
                    result = service.renewBaselineLTA(content, source, tsa, policy);
                } else {
                    var baselineLT = request.revocationValues().isEmpty()
                            ? source
                            : service.augmentToBaselineLT(source, material(request))
                                    .encodedSignature();
                    result = service.augmentToBaselineLTA(
                            content, baselineLT, tsa, policy);
                }
            }
            var id = UUID.randomUUID();
            var sourceDigest = digest(source);
            var resultDigest = digest(result.encodedSignature());
            repository.save(SignatureAugmentationEntity.create(
                    id,
                    sourceDigest,
                    resultDigest,
                    result.level(),
                    result.certificateCount(),
                    result.revocationValueCount(),
                    result.archiveTimestampCount(),
                    result.archiveTimestampPolicyOid(),
                    clock.instant()));
            return new LongTermAugmentationResponse(
                    id,
                    Base64.getEncoder().encodeToString(result.encodedSignature()),
                    result.level(),
                    sourceDigest,
                    resultDigest,
                    result.certificateCount(),
                    result.revocationValueCount(),
                    result.archiveTimestampCount(),
                    result.archiveTimestampGenerationTime(),
                    result.archiveTimestampPolicyOid());
        } catch (ApiException exception) {
            throw exception;
        } catch (CadesException exception) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    exception.code(),
                    exception.getMessage(),
                    false);
        }
    }

    private String timestampPolicyOid() {
        if (tsaProfiles != null) {
            var value = tsaProfiles.current().archivePolicyOid();
            if (value != null && !value.isBlank()) return value;
        }
        return timestampProperties.getArchivePolicyOid();
    }

    private static CadesValidationMaterial material(LongTermAugmentationRequest request) {
        return new CadesValidationMaterial(
                request.certificates().stream()
                        .map(item -> BoundedBase64Decoder.decode(
                                item, "certificates", ApiLimits.MAX_CERTIFICATE_BYTES))
                        .toList(),
                request.revocationValues().stream()
                        .map(item -> new CadesRevocationValue(
                                item.type(),
                                BoundedBase64Decoder.decode(
                                        item.value(),
                                        "revocationValues.value",
                                        ApiLimits.MAX_REVOCATION_VALUE_BYTES)))
                        .toList());
    }

    private static void requireContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CONTENT_REQUIRED_FOR_ARCHIVE_TIMESTAMP",
                    "B-LTA arşiv zaman damgası için ayrık içerik zorunludur.",
                    false);
        }
    }

    private static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
