package io.github.erbayaskin.eimza.validation;

import java.time.Duration;
import java.util.Set;

public record CertificateValidationPolicy(
        int minimumRsaBits,
        int minimumEcBits,
        boolean requireDigitalSignatureKeyUsage,
        boolean requireQcCompliance,
        Set<String> requiredCertificatePolicyOids,
        boolean revocationRequired,
        Duration maximumRevocationAge,
        Duration clockSkew) {

    public CertificateValidationPolicy {
        if (minimumRsaBits < 2048 || minimumEcBits < 256) {
            throw new IllegalArgumentException("Asgari anahtar boyutu güvenli tabanın altında olamaz.");
        }
        requiredCertificatePolicyOids = Set.copyOf(requiredCertificatePolicyOids);
        if (maximumRevocationAge.isNegative() || maximumRevocationAge.isZero()
                || clockSkew.isNegative()) {
            throw new IllegalArgumentException("İptal kanıtı süreleri geçersiz.");
        }
    }

    public static CertificateValidationPolicy turkishQualifiedBaseline() {
        return new CertificateValidationPolicy(
                2048,
                256,
                true,
                true,
                Set.of(),
                true,
                Duration.ofHours(24),
                Duration.ofMinutes(5));
    }
}
