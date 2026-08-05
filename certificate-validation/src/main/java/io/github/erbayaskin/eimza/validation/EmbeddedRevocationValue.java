package io.github.erbayaskin.eimza.validation;

public record EmbeddedRevocationValue(String sourceType, byte[] encodedValue) {

    public EmbeddedRevocationValue {
        if (!"CRL".equals(sourceType) && !"OCSP".equals(sourceType)) {
            throw new IllegalArgumentException("Gömülü iptal kanıtı CRL veya OCSP olmalıdır.");
        }
        encodedValue = encodedValue.clone();
    }

    @Override
    public byte[] encodedValue() {
        return encodedValue.clone();
    }
}
