package io.github.erbayaskin.eimza.smartcard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AtrPatternTest {

    @Test
    void normalizesCommonHexSeparators() {
        assertThat(Atr.fromHex("3b:95 95-40").hex()).isEqualTo("3B959540");
    }

    @Test
    void appliesMaskWithoutTreatingAtrAsTrustEvidence() {
        var pattern =
                new AtrPattern(
                        Atr.fromHex("3B950040"),
                        Atr.fromHex("FFFF00FF"));

        assertThat(pattern.matches(Atr.fromHex("3B95AA40"))).isTrue();
        assertThat(pattern.matches(Atr.fromHex("3B96AA40"))).isFalse();
    }
}
