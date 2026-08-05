package io.github.erbayaskin.eimza.xades;

import java.time.Instant;

public record XadesSignatureResult(
        byte[] encodedSignature,
        String level,
        Instant timestampGenerationTime,
        String timestampPolicyOid) {
    public XadesSignatureResult {
        encodedSignature = encodedSignature.clone();
    }
    @Override public byte[] encodedSignature() { return encodedSignature.clone(); }
}
