package io.github.erbayaskin.eimza.api.clientdevice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_device")
class ClientDeviceEntity {
    @Id
    @Column(name = "device_id")
    private UUID deviceId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Column(nullable = false)
    private String algorithm;
    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClientDeviceEntity() {}

    static ClientDeviceEntity create(
            UUID deviceId, UUID tenantId, String displayName, byte[] publicKey, Instant now) {
        var value = new ClientDeviceEntity();
        value.deviceId = deviceId;
        value.tenantId = tenantId;
        value.displayName = displayName;
        value.algorithm = "Ed25519";
        value.publicKey = publicKey.clone();
        value.enabled = true;
        value.createdAt = now;
        value.updatedAt = now;
        return value;
    }

    UUID deviceId() { return deviceId; }
    UUID tenantId() { return tenantId; }
    String displayName() { return displayName; }
    byte[] publicKey() { return publicKey.clone(); }
    boolean enabled() { return enabled; }
}
