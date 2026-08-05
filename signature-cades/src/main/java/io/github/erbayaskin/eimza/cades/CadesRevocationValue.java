package io.github.erbayaskin.eimza.cades;

public record CadesRevocationValue(CadesRevocationType type, byte[] encodedValue) {

    public CadesRevocationValue {
        if (type == null) {
            throw new IllegalArgumentException("İptal kanıtı türü zorunludur.");
        }
        if (encodedValue == null || encodedValue.length == 0) {
            throw new IllegalArgumentException("İptal kanıtı boş olamaz.");
        }
        encodedValue = encodedValue.clone();
    }

    @Override
    public byte[] encodedValue() {
        return encodedValue.clone();
    }
}
