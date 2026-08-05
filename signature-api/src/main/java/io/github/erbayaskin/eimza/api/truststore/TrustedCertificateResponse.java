package io.github.erbayaskin.eimza.api.truststore;

import java.time.Instant;
import java.util.UUID;

public record TrustedCertificateResponse(
        UUID certificateId,
        String fingerprintSha256,
        String subjectDn,
        String issuerDn,
        String serialNumberHex,
        Instant notBefore,
        Instant notAfter,
        TrustedCertificateType trustType,
        String displayName,
        boolean enabled) {

    static TrustedCertificateResponse from(TrustStoreEntryEntity entry) {
        var certificate = entry.certificate();
        return new TrustedCertificateResponse(
                certificate.id(),
                certificate.fingerprintSha256(),
                certificate.subjectDn(),
                certificate.issuerDn(),
                certificate.serialNumberHex(),
                certificate.notBefore(),
                certificate.notAfter(),
                entry.trustType(),
                entry.displayName(),
                entry.enabled());
    }
}
