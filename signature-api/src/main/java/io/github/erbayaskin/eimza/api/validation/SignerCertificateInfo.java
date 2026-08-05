package io.github.erbayaskin.eimza.api.validation;

import java.time.Instant;

public record SignerCertificateInfo(
        String commonName,
        String subjectDn,
        String serialNumberAttribute,
        String organization,
        String organizationalUnit,
        String country,
        String issuerDn,
        String certificateSerialNumber,
        Instant validFrom,
        Instant validUntil,
        String publicKeyAlgorithm,
        String sha256Fingerprint,
        String certificateBase64) {}
