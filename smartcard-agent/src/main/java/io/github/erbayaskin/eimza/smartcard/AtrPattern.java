package io.github.erbayaskin.eimza.smartcard;

import java.util.Objects;

public record AtrPattern(Atr value, Atr mask) {

    public AtrPattern {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(mask, "mask");
        if (value.bytes().length != mask.bytes().length) {
            throw new IllegalArgumentException("ATR değeri ile maske aynı uzunlukta olmalıdır.");
        }
    }

    public boolean matches(Atr candidate) {
        var expected = value.bytes();
        var bitMask = mask.bytes();
        var actual = candidate.bytes();
        if (actual.length != expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((actual[index] & bitMask[index]) != (expected[index] & bitMask[index])) {
                return false;
            }
        }
        return true;
    }
}
