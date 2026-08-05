package io.github.erbayaskin.eimza.smartcard.token;

import java.time.Instant;

public record CardCertificate(
        String fingerprintSha256,
        String subject,
        String issuer,
        String serialNumber,
        Instant notBefore,
        Instant notAfter,
        String publicKeyAlgorithm,
        boolean hasPrivateKey,
        String certificateBase64) {}
