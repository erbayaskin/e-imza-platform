package io.github.erbayaskin.eimza.api.signing;

import java.util.Base64;
import java.util.UUID;
import io.github.erbayaskin.eimza.core.model.SignatureFormat;
import io.github.erbayaskin.eimza.core.model.SignatureLevel;

public record SignatureArtifactResponse(
        UUID artifactId,
        SignatureFormat format,
        SignatureLevel level,
        String mediaType,
        String sha256,
        String artifactBase64) {
    static SignatureArtifactResponse from(SignatureArtifactEntity entity) {
        return new SignatureArtifactResponse(
                entity.id(), entity.format(), entity.level(), entity.mediaType(),
                entity.artifactDigest(),
                Base64.getEncoder().encodeToString(entity.encodedArtifact()));
    }
}
