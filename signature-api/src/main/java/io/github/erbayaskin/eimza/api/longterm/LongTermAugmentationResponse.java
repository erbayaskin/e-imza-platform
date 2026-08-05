package io.github.erbayaskin.eimza.api.longterm;

import java.time.Instant;
import java.util.UUID;

public record LongTermAugmentationResponse(
        UUID augmentationId,
        String signature,
        String level,
        String sourceDigest,
        String resultDigest,
        int certificateCount,
        int revocationValueCount,
        int archiveTimestampCount,
        Instant archiveTimestampGenerationTime,
        String archiveTimestampPolicyOid) {
}
