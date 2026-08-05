package io.github.erbayaskin.eimza.api.truststore;

import java.time.Instant;
import java.util.List;

public record TrustStoreSnapshotResponse(
        String version,
        String contentDigest,
        Instant validFrom,
        Instant validUntil,
        List<TrustedCertificateResponse> certificates) {

    public TrustStoreSnapshotResponse {
        certificates = List.copyOf(certificates);
    }

    static TrustStoreSnapshotResponse empty() {
        return new TrustStoreSnapshotResponse(null, null, null, null, List.of());
    }
}
