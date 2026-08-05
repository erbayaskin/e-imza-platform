package io.github.erbayaskin.eimza.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.api.error.ApiException;

class BoundedBase64DecoderTest {

    @Test
    void decodesWithinLimit() {
        var encoded = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});

        assertThat(BoundedBase64Decoder.decode(encoded, "content", 3))
                .containsExactly(1, 2, 3);
    }

    @Test
    void rejectsOversizedAndMalformedValues() {
        var oversized = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4});

        assertThatThrownBy(() -> BoundedBase64Decoder.decode(oversized, "content", 3))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code())
                        .isEqualTo("FIELD_TOO_LARGE"));
        assertThatThrownBy(() -> BoundedBase64Decoder.decode("%%%", "content", 3))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code())
                        .isEqualTo("INVALID_BASE64"));
    }
}
