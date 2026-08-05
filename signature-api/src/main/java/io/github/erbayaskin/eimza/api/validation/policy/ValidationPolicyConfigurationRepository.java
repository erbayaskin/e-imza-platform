package io.github.erbayaskin.eimza.api.validation.policy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ValidationPolicyConfigurationRepository
        extends JpaRepository<ValidationPolicyConfigurationEntity, UUID> {

    Optional<ValidationPolicyConfigurationEntity> findFirstByOrderByVersionNumberDesc();
}
