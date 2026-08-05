package io.github.erbayaskin.eimza.api.truststore;

import java.time.Instant;

record ParsedTrustedCertificate(
        String fingerprintSha256,
        String subjectDn,
        String issuerDn,
        String serialNumberHex,
        Instant notBefore,
        Instant notAfter,
        String certificateBase64) {
}
