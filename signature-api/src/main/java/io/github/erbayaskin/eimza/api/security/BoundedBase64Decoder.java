package io.github.erbayaskin.eimza.api.security;

import java.util.Base64;
import org.springframework.http.HttpStatus;
import io.github.erbayaskin.eimza.api.error.ApiException;

public final class BoundedBase64Decoder {

    private BoundedBase64Decoder() {}

    public static byte[] decode(String value, String field, int maximumBytes) {
        var maximumEncodedLength = ((long) maximumBytes + 2L) / 3L * 4L;
        if (value == null || value.length() > maximumEncodedLength) {
            throw tooLarge(field, maximumBytes);
        }
        try {
            var decoded = Base64.getDecoder().decode(value);
            if (decoded.length > maximumBytes) {
                throw tooLarge(field, maximumBytes);
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_BASE64",
                    field + " Base64 kodlaması geçersiz.",
                    false);
        }
    }

    private static ApiException tooLarge(String field, int maximumBytes) {
        return new ApiException(
                HttpStatus.CONTENT_TOO_LARGE,
                "FIELD_TOO_LARGE",
                field + " izin verilen " + maximumBytes + " bayt sınırını aşıyor.",
                false);
    }
}
