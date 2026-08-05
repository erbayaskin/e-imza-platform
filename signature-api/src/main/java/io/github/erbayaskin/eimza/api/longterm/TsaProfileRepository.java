package io.github.erbayaskin.eimza.api.longterm;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TsaProfileRepository extends JpaRepository<TsaProfileEntity, UUID> {
    Optional<TsaProfileEntity> findFirstByOrderByVersionNumberDesc();
}
