package io.github.erbayaskin.eimza.api.longterm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LongTermAugmentationRequest(
        @NotBlank @Size(max = io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_BASE64_DOCUMENT_CHARS)
                String signature,
        @Size(max = io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_BASE64_DOCUMENT_CHARS) String content,
        @NotNull LongTermTarget target,
        @Size(max = 20)
                List<@NotBlank
                                @Size(max = io.github.erbayaskin.eimza.api.security.ApiLimits.MAX_BASE64_CERTIFICATE_CHARS)
                                String>
                        certificates,
        @Size(max = 100) List<@Valid LongTermRevocationValueRequest> revocationValues,
        @Size(max = 128) String timestampPolicyOid) {

    public LongTermAugmentationRequest {
        certificates = certificates == null ? List.of() : List.copyOf(certificates);
        revocationValues = revocationValues == null ? List.of() : List.copyOf(revocationValues);
    }
}
