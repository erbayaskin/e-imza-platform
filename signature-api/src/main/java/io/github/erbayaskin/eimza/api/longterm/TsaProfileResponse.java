package io.github.erbayaskin.eimza.api.longterm;

import java.time.Instant;

public record TsaProfileResponse(
        long version, String providerId, String endpoint, int requestTimeoutSeconds,
        String credentialRef, String archivePolicyOid, boolean enabled, Instant createdAt,
        String source) {
    static TsaProfileResponse from(TsaProfileEntity value) {
        return new TsaProfileResponse(
                value.versionNumber(), value.providerId(), value.endpoint(),
                value.requestTimeoutSeconds(), value.credentialRef(),
                value.archivePolicyOid(), value.enabled(), value.createdAt(), "DATABASE");
    }
}
