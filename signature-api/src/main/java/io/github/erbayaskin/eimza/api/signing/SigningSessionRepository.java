package io.github.erbayaskin.eimza.api.signing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SigningSessionRepository extends JpaRepository<SigningSessionEntity, UUID> {

    Optional<SigningSessionEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<SigningSessionEntity> findByTenantIdAndSubjectIdAndIdempotencyKey(
            UUID tenantId,
            String subjectId,
            String idempotencyKey);
}
