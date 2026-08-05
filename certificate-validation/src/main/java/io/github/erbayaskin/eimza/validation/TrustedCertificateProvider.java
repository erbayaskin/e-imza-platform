package io.github.erbayaskin.eimza.validation;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

/**
 * Supplies the immutable trusted-certificate snapshot that was effective at a validation time.
 */
public interface TrustedCertificateProvider {

    TrustedCertificateSnapshot snapshotAt(Instant validationTime);

    record TrustedCertificateSnapshot(
            String version,
            Instant validFrom,
            Instant validUntil,
            List<TrustedCertificate> certificates) {

        public TrustedCertificateSnapshot {
            certificates = List.copyOf(certificates);
        }
    }

    record TrustedCertificate(
            X509Certificate certificate,
            TrustedCertificateType type,
            String fingerprintSha256) {
    }

    enum TrustedCertificateType {
        ROOT,
        INTERMEDIATE
    }
}
