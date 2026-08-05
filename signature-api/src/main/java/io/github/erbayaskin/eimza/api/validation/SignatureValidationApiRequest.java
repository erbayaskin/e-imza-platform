package io.github.erbayaskin.eimza.api.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record SignatureValidationApiRequest(
        @Size(max = io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_BASE64_DOCUMENT_CHARS)
                String content,
        @NotBlank @Size(max = io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_BASE64_DOCUMENT_CHARS)
                String signature,
        Instant validationTime,
        @Size(max = 16) String format,
        @Size(max = 20) String signaturePackaging) {

    public SignatureValidationApiRequest {
        format = format == null || format.isBlank()
                ? "CADES"
                : format.trim().toUpperCase(java.util.Locale.ROOT);
        signaturePackaging = signaturePackaging == null || signaturePackaging.isBlank()
                ? "AUTO"
                : signaturePackaging.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public SignatureValidationApiRequest(
            String content, String signature, Instant validationTime) {
        this(content, signature, validationTime, "CADES", "AUTO");
    }

    public SignatureValidationApiRequest(
            String content, String signature, Instant validationTime, String format) {
        this(content, signature, validationTime, format, "AUTO");
    }
}
