package io.github.erbayaskin.eimza.api.serversigning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ServerKeyProfileRepository extends JpaRepository<ServerKeyProfileEntity, UUID> {
    Optional<ServerKeyProfileEntity> findByServerKeyIdAndEnabledTrue(String serverKeyId);
    List<ServerKeyProfileEntity> findAllByOrderByDisplayNameAsc();
}
