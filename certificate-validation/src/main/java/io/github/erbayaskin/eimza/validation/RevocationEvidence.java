package io.github.erbayaskin.eimza.validation;

import java.time.Instant;

public record RevocationEvidence(
        RevocationStatus status,
        String sourceType,
        Instant producedAt,
        Instant thisUpdate,
        Instant validUntil,
        Instant revocationTime,
        String sourceUri,
        byte[] encodedEvidence) {

    public RevocationEvidence {
        encodedEvidence = encodedEvidence == null ? new byte[0] : encodedEvidence.clone();
    }

    @Override
    public byte[] encodedEvidence() {
        return encodedEvidence.clone();
    }
}
