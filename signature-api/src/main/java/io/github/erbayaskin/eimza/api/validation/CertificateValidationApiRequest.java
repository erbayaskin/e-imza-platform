package io.github.erbayaskin.eimza.api.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CertificateValidationApiRequest(
        @NotBlank @Size(max = io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_BASE64_CERTIFICATE_CHARS)
                String certificate,
        @Size(max = 20)
                List<@NotBlank
                                @Size(max = io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_BASE64_CERTIFICATE_CHARS)
                                String>
                        intermediateCertificates,
        Instant validationTime) {

    public CertificateValidationApiRequest {
        intermediateCertificates =
                intermediateCertificates == null ? List.of() : List.copyOf(intermediateCertificates);
    }
}
