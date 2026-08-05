package io.github.erbayaskin.eimza.smartcard;

import java.util.HexFormat;
import java.util.Objects;

public record Atr(byte[] bytes) {

    public Atr {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("ATR boş olamaz.");
        }
        bytes = bytes.clone();
    }

    public static Atr fromHex(String value) {
        Objects.requireNonNull(value, "value");
        var normalized = value.replaceAll("[\\s:-]", "");
        if (normalized.isEmpty() || (normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("ATR geçerli çift uzunluklu hex olmalıdır.");
        }
        return new Atr(HexFormat.of().parseHex(normalized));
    }

    public String hex() {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
