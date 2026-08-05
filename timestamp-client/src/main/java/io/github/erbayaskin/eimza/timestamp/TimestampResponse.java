package io.github.erbayaskin.eimza.timestamp;

import java.time.Instant;
import java.math.BigInteger;

public record TimestampResponse(
        byte[] encodedToken,
        Instant generationTime,
        String policyOid,
        BigInteger serialNumber,
        String providerId) {

    public TimestampResponse {
        encodedToken = encodedToken.clone();
    }

    @Override
    public byte[] encodedToken() {
        return encodedToken.clone();
    }
}
