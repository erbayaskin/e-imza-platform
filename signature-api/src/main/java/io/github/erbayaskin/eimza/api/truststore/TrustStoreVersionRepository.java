package io.github.erbayaskin.eimza.api.truststore;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TrustStoreVersionRepository extends JpaRepository<TrustStoreVersionEntity, UUID> {

    Optional<TrustStoreVersionEntity> findFirstByStatusOrderByValidFromDesc(String status);

    @Query("""
            select version
              from TrustStoreVersionEntity version
             where version.validFrom <= :validationTime
               and (version.validUntil is null or version.validUntil > :validationTime)
             order by version.validFrom desc
            limit 1
            """)
    Optional<TrustStoreVersionEntity> findEffectiveAt(
            @Param("validationTime") Instant validationTime);
}
