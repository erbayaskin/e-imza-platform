package io.github.erbayaskin.eimza.cades;

import java.util.List;

public record CadesInspection(
        byte[] signerCertificate,
        List<byte[]> embeddedCertificates,
        List<CadesRevocationValue> embeddedRevocationValues) {

    public CadesInspection {
        signerCertificate = signerCertificate.clone();
        embeddedCertificates = embeddedCertificates.stream().map(byte[]::clone).toList();
        embeddedRevocationValues = List.copyOf(embeddedRevocationValues);
    }

    @Override public byte[] signerCertificate() { return signerCertificate.clone(); }
    @Override public List<byte[]> embeddedCertificates() {
        return embeddedCertificates.stream().map(byte[]::clone).toList();
    }
}
