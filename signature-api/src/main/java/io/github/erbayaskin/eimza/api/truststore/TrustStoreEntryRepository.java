package io.github.erbayaskin.eimza.api.truststore;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface TrustStoreEntryRepository extends JpaRepository<TrustStoreEntryEntity, UUID> {

    @EntityGraph(attributePaths = "trustedCertificate")
    List<TrustStoreEntryEntity> findByTrustStoreVersion_IdOrderByDisplayName(UUID versionId);
}
