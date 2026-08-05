package io.github.erbayaskin.eimza.cades;

import java.time.Instant;

public record CadesAugmentationResult(
        byte[] encodedSignature,
        String level,
        int certificateCount,
        int revocationValueCount,
        int archiveTimestampCount,
        Instant archiveTimestampGenerationTime,
        String archiveTimestampPolicyOid) {

    public CadesAugmentationResult {
        encodedSignature = encodedSignature.clone();
    }

    @Override
    public byte[] encodedSignature() {
        return encodedSignature.clone();
    }
}
