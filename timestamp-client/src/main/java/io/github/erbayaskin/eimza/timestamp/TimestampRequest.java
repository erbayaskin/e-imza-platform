package io.github.erbayaskin.eimza.timestamp;

import java.math.BigInteger;
import java.util.Objects;

public record TimestampRequest(
        String digestAlgorithmOid,
        byte[] messageImprint,
        BigInteger nonce,
        String requestedPolicyOid) {

    public TimestampRequest {
        Objects.requireNonNull(digestAlgorithmOid, "digestAlgorithmOid");
        Objects.requireNonNull(messageImprint, "messageImprint");
        Objects.requireNonNull(nonce, "nonce");
        if ("2.16.840.1.101.3.4.2.1".equals(digestAlgorithmOid) && messageImprint.length != 32) {
            throw new IllegalArgumentException("SHA-256 messageImprint 32 bayt olmalıdır.");
        }
        messageImprint = messageImprint.clone();
    }

    @Override
    public byte[] messageImprint() {
        return messageImprint.clone();
    }
}
