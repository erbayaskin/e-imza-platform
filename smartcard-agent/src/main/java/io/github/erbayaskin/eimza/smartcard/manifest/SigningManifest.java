package io.github.erbayaskin.eimza.smartcard.manifest;

import java.time.Instant;

public record SigningManifest(
        String sessionId,
        String deviceId,
        String readerId,
        String certificateFingerprint,
        String digestAlgorithm,
        String digest,
        String signatureAlgorithm,
        String nonce,
        Instant issuedAt,
        Instant expiresAt) {}
