package io.github.erbayaskin.eimza.validation;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

/**
 * Validates CAdES-embedded evidence first and uses the configured provider only
 * when no embedded evidence applies to the requested certificate.
 */
public final class EmbeddedFirstRevocationDataProvider implements RevocationDataProvider {

    private final List<EmbeddedRevocationValue> values;
    private final NetworkRevocationDataProvider evidenceValidator;
    private final RevocationDataProvider fallback;

    public EmbeddedFirstRevocationDataProvider(
            List<EmbeddedRevocationValue> values,
            NetworkRevocationDataProvider evidenceValidator,
            RevocationDataProvider fallback) {
        this.values = List.copyOf(values);
        this.evidenceValidator = evidenceValidator;
        this.fallback = fallback;
    }

    @Override
    public RevocationEvidence resolve(
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime) {
        for (var value : values) {
            try {
                return "OCSP".equals(value.sourceType())
                        ? evidenceValidator.evaluateOcsp(
                                value.encodedValue(), certificate, issuer, validationTime)
                        : evidenceValidator.evaluateCrl(
                                value.encodedValue(), certificate, issuer, validationTime);
            } catch (Exception ignored) {
                // Kanıt başka bir zincir öğesine ait olabilir; sonraki değer denenir.
            }
        }
        return fallback.resolve(certificate, issuer, validationTime);
    }
}
