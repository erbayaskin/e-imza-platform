package io.github.erbayaskin.eimza.cades;

import java.time.Instant;
import java.util.List;

public record CadesLongTermVerificationResult(
        String level,
        int embeddedCertificateCount,
        int embeddedRevocationValueCount,
        List<Instant> archiveTimestampGenerationTimes,
        List<String> archiveTimestampPolicyOids) {

    public CadesLongTermVerificationResult {
        archiveTimestampGenerationTimes = List.copyOf(archiveTimestampGenerationTimes);
        archiveTimestampPolicyOids = List.copyOf(archiveTimestampPolicyOids);
    }
}
