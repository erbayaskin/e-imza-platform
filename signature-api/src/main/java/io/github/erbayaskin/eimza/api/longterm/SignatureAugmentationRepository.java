package io.github.erbayaskin.eimza.api.longterm;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SignatureAugmentationRepository
        extends JpaRepository<SignatureAugmentationEntity, UUID> {
}
