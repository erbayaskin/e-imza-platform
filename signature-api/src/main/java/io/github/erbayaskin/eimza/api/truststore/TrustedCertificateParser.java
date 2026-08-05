package io.github.erbayaskin.eimza.api.truststore;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Component
class TrustedCertificateParser {

    ParsedTrustedCertificate parse(String pemOrBase64Der) {
        try {
            var encoded = decode(pemOrBase64Der);
            var factory = CertificateFactory.getInstance("X.509");
            var certificate =
                    (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(encoded));
            requireCertificateAuthority(certificate);
            var canonicalEncoded = certificate.getEncoded();
            return new ParsedTrustedCertificate(
                    fingerprint(canonicalEncoded),
                    certificate.getSubjectX500Principal().getName(),
                    certificate.getIssuerX500Principal().getName(),
                    certificate.getSerialNumber().toString(16).toUpperCase(),
                    certificate.getNotBefore().toInstant(),
                    certificate.getNotAfter().toInstant(),
                    Base64.getEncoder().encodeToString(canonicalEncoded));
        } catch (CertificateException | IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TRUSTED_CERTIFICATE_INVALID",
                    "Sertifika geçerli X.509 CA sertifikası değil.",
                    false);
        }
    }

    X509Certificate decodeEntity(TrustedCertificateEntity entity) {
        try {
            var factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate)
                    factory.generateCertificate(
                            new ByteArrayInputStream(
                                    Base64.getDecoder().decode(entity.certificateBase64())));
        } catch (CertificateException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Güven deposundaki sertifika ayrıştırılamadı: " + entity.id(), exception);
        }
    }

    private byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sertifika boş olamaz.");
        }
        var normalized =
                value.replace("-----BEGIN CERTIFICATE-----", "")
                        .replace("-----END CERTIFICATE-----", "")
                        .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private void requireCertificateAuthority(X509Certificate certificate) {
        if (certificate.getBasicConstraints() < 0) {
            throw new IllegalArgumentException("Sertifika CA sertifikası değil.");
        }
        var keyUsage = certificate.getKeyUsage();
        if (keyUsage != null && (keyUsage.length <= 5 || !keyUsage[5])) {
            throw new IllegalArgumentException("Sertifikada keyCertSign kullanımı yok.");
        }
    }

    private String fingerprint(byte[] encoded) {
        try {
            return HexFormat.of()
                    .withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM SHA-256 algoritmasını sağlamıyor.", exception);
        }
    }
}
