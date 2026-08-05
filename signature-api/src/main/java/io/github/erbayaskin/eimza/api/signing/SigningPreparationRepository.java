package io.github.erbayaskin.eimza.api.signing;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SigningPreparationRepository
        extends JpaRepository<SigningPreparationEntity, UUID> {}
