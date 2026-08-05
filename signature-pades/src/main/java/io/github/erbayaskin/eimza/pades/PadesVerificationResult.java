package io.github.erbayaskin.eimza.pades;

import java.util.List;
import io.github.erbayaskin.eimza.cades.CadesVerificationResult;

public record PadesVerificationResult(
        CadesVerificationResult cades,
        byte[] signerCertificate,
        List<byte[]> embeddedCertificates) {

    public PadesVerificationResult {
        signerCertificate = signerCertificate.clone();
        embeddedCertificates = embeddedCertificates.stream().map(byte[]::clone).toList();
    }

    @Override public byte[] signerCertificate() { return signerCertificate.clone(); }
    @Override public List<byte[]> embeddedCertificates() {
        return embeddedCertificates.stream().map(byte[]::clone).toList();
    }
}
