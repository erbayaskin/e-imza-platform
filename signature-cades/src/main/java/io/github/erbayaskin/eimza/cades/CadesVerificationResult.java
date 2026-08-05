package io.github.erbayaskin.eimza.cades;

import java.time.Instant;
import java.util.List;

public record CadesVerificationResult(
        boolean cryptographicValidity,
        String format,
        String level,
        String signaturePolicyOid,
        Instant timestampGenerationTime,
        String timestampPolicyOid,
        String revocationStatus,
        int embeddedCertificateCount,
        int embeddedRevocationValueCount,
        List<Instant> archiveTimestampGenerationTimes) {

    public CadesVerificationResult {
        archiveTimestampGenerationTimes = List.copyOf(archiveTimestampGenerationTimes);
    }
}
