package io.github.erbayaskin.eimza.api.signing;

import java.time.Instant;

public record SigningManifestResponse(
        String manifest,
        String signature,
        String keyId,
        String manifestPublicKey,
        Instant expiresAt) {}
