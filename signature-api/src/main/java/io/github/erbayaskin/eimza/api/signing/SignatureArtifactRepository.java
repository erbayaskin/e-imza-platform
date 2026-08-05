package io.github.erbayaskin.eimza.api.signing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SignatureArtifactRepository extends JpaRepository<SignatureArtifactEntity, UUID> {
    Optional<SignatureArtifactEntity> findBySessionId(UUID sessionId);
}
