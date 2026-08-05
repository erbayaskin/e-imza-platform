package io.github.erbayaskin.eimza.api.clientdevice;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClientDeviceRepository extends JpaRepository<ClientDeviceEntity, UUID> {
    Optional<ClientDeviceEntity> findByTenantIdAndDeviceId(UUID tenantId, UUID deviceId);
}
