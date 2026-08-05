package io.github.erbayaskin.eimza.cades;

import java.util.Objects;

public record SignaturePolicy(
        String oid,
        String digestAlgorithmOid,
        byte[] digest,
        String uri) {

    public SignaturePolicy {
        Objects.requireNonNull(oid, "oid");
        Objects.requireNonNull(digestAlgorithmOid, "digestAlgorithmOid");
        Objects.requireNonNull(digest, "digest");
        if (digest.length == 0) {
            throw new IllegalArgumentException("Politika özeti boş olamaz.");
        }
        digest = digest.clone();
    }

    @Override
    public byte[] digest() {
        return digest.clone();
    }
}
