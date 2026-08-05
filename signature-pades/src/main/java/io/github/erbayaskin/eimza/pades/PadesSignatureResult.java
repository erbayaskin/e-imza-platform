package io.github.erbayaskin.eimza.pades;

import java.time.Instant;

public record PadesSignatureResult(
        byte[] encodedPdf,
        String level,
        Instant timestampGenerationTime,
        String timestampPolicyOid) {
    public PadesSignatureResult {
        encodedPdf = encodedPdf.clone();
    }
    @Override public byte[] encodedPdf() { return encodedPdf.clone(); }
}
