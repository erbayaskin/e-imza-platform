package io.github.erbayaskin.eimza.cades;

import java.util.List;

public record CadesValidationMaterial(
        List<byte[]> certificates,
        List<CadesRevocationValue> revocationValues) {

    public CadesValidationMaterial {
        certificates = certificates == null
                ? List.of()
                : certificates.stream().map(byte[]::clone).toList();
        revocationValues = revocationValues == null ? List.of() : List.copyOf(revocationValues);
    }

    @Override
    public List<byte[]> certificates() {
        return certificates.stream().map(byte[]::clone).toList();
    }
}
