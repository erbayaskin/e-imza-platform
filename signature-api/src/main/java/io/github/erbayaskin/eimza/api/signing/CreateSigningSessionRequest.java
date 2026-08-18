package io.github.erbayaskin.eimza.api.signing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignatureLevel;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;
import io.github.erbayaskin.eimza.core.model.SigningMode;
import io.github.erbayaskin.eimza.core.model.TurkishSignatureProfile;

public record CreateSigningSessionRequest(
        @NotNull UUID documentId,
        @Valid DocumentDigest documentDigest,
        @NotBlank @Size(max = 255) String documentName,
        @NotBlank @Size(max = 100) String mediaType,
        @Positive @Max(io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_DOCUMENT_BYTES) long size,
        @NotNull SignatureFormat format,
        @NotNull SignatureLevel targetLevel,
        @NotNull TurkishSignatureProfile turkishProfile,
        @NotBlank @Size(max = 250) String purpose,
        SigningMode signingMode,
        UUID deviceId,
        @Size(max = 100) String serverKeyId,
        @Size(max = 36_700_160) String documentBase64,
        SignaturePackaging signaturePackaging,
        @Size(max = 40) String signatureAlgorithm,
        MultiSignatureType multiSignatureType,
        @Size(max = 36_700_160) String existingArtifactBase64,
        @PositiveOrZero Integer targetSignatureIndex) {

    public record DocumentDigest(
            @NotBlank @Size(max = 32) String algorithm,
            @NotBlank @Size(max = 128) String value) {
    }
}
