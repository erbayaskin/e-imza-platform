package io.github.erbayaskin.eimza.api.serversigning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "server_key_profile")
class ServerKeyProfileEntity {
    @Id private UUID id;
    @Column(name = "server_key_id", nullable = false, unique = true, length = 36)
    private String serverKeyId;
    @Column(name = "display_name", nullable = false, length = 250)
    private String displayName;
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    private ServerDeviceType deviceType;
    @Column(name = "pkcs11_library", nullable = false, length = 1000)
    private String pkcs11Library;
    @Column(name = "slot_list_index")
    private Integer slotListIndex;
    @Column(length = 256) private String atr;
    @Column(name = "atr_mask", length = 256) private String atrMask;
    @Column(name = "certificate_fingerprint", length = 128)
    private String certificateFingerprint;
    @Column(name = "credential_ref", length = 250)
    private String credentialRef;
    @Column(name = "allowed_tenant_ids", nullable = false)
    private String allowedTenantIds;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ServerKeyProfileEntity() {}

    static ServerKeyProfileEntity create(
            UUID id, CreateServerKeyProfileRequest request, Instant now) {
        var value = new ServerKeyProfileEntity();
        value.id = id;
        value.serverKeyId = id.toString();
        value.createdAt = now;
        value.apply(request.displayName(), request.deviceType(), request.pkcs11Library(),
                request.slotListIndex(), request.atr(), request.atrMask(),
                request.certificateFingerprint(), request.credentialRef(),
                request.allowedTenantIds(), request.enabled(), now);
        return value;
    }

    void update(UpdateServerKeyProfileRequest request, Instant now) {
        apply(request.displayName(), request.deviceType(), request.pkcs11Library(),
                request.slotListIndex(), request.atr(), request.atrMask(),
                request.certificateFingerprint(), request.credentialRef(),
                request.allowedTenantIds(), request.enabled(), now);
    }

    private void apply(
            String displayName, ServerDeviceType deviceType, String pkcs11Library,
            Integer slotListIndex, String atr, String atrMask, String certificateFingerprint,
            String credentialRef, List<UUID> tenants, boolean enabled, Instant now) {
        this.displayName = displayName.trim();
        this.deviceType = deviceType;
        this.pkcs11Library = pkcs11Library.trim();
        this.slotListIndex = slotListIndex;
        this.atr = normalizeHex(atr);
        this.atrMask = normalizeHex(atrMask);
        this.certificateFingerprint = normalizeHex(certificateFingerprint);
        this.credentialRef = blankToNull(credentialRef);
        this.allowedTenantIds = tenants.stream().map(UUID::toString)
                .sorted().collect(java.util.stream.Collectors.joining(","));
        this.enabled = enabled;
        this.updatedAt = now;
    }

    private static String normalizeHex(String value) {
        return value == null || value.isBlank()
                ? null : value.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
    }
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    UUID id() { return id; }
    String serverKeyId() { return serverKeyId; }
    String displayName() { return displayName; }
    ServerDeviceType deviceType() { return deviceType; }
    String pkcs11Library() { return pkcs11Library; }
    Integer slotListIndex() { return slotListIndex; }
    String atr() { return atr; }
    String atrMask() { return atrMask; }
    String certificateFingerprint() { return certificateFingerprint; }
    String credentialRef() { return credentialRef; }
    List<UUID> allowedTenantIds() {
        return allowedTenantIds.isBlank() ? List.of()
                : java.util.Arrays.stream(allowedTenantIds.split(",")).map(UUID::fromString).toList();
    }
    boolean enabled() { return enabled; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
