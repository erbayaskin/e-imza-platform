package io.github.erbayaskin.eimza.api.signing;

import java.time.Instant;
import java.util.UUID;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignatureLevel;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;
import io.github.erbayaskin.eimza.core.model.SigningMode;
import io.github.erbayaskin.eimza.core.model.TurkishSignatureProfile;
import io.github.erbayaskin.eimza.core.signing.SigningSessionStatus;

public record SigningSessionResponse(
        UUID sessionId,
        SigningSessionStatus status,
        SigningMode signingMode,
        String serverKeyId,
        SignatureFormat format,
        SignaturePackaging signaturePackaging,
        String signatureAlgorithm,
        MultiSignatureType multiSignatureType,
        int targetSignatureIndex,
        SignatureLevel targetLevel,
        TurkishSignatureProfile turkishProfile,
        Instant expiresAt,
        Instant createdAt,
        String manifestStatus) {

    static SigningSessionResponse from(SigningSessionEntity entity) {
        return new SigningSessionResponse(
                entity.id(),
                entity.status(),
                entity.signingMode(),
                entity.serverKeyId(),
                entity.format(),
                entity.signaturePackaging(),
                entity.requestedSignatureAlgorithm(),
                entity.multiSignatureType(),
                entity.targetSignatureIndex(),
                entity.targetLevel(),
                entity.turkishProfile(),
                entity.expiresAt(),
                entity.createdAt(),
                switch (entity.status()) {
                    case CREATED -> "NOT_ISSUED";
                    case MANIFEST_ISSUED -> "ISSUED";
                    default -> "CONSUMED";
                });
    }
}
