package io.github.erbayaskin.eimza.cades;

import java.time.Instant;

public record CadesSignatureResult(
        byte[] encodedSignature,
        String format,
        String level,
        String digestAlgorithm,
        String signatureAlgorithm,
        String signaturePolicyOid,
        Instant timestampGenerationTime,
        String timestampPolicyOid) {

    public CadesSignatureResult {
        encodedSignature = encodedSignature.clone();
    }

    @Override
    public byte[] encodedSignature() {
        return encodedSignature.clone();
    }
}
