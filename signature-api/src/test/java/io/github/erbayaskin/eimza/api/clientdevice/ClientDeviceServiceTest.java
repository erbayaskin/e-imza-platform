package io.github.erbayaskin.eimza.api.clientdevice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.github.erbayaskin.eimza.api.error.ApiException;

@ExtendWith(MockitoExtension.class)
class ClientDeviceServiceTest {
    private static final UUID DEVICE_ID = UUID.fromString("a8a0dc09-54ab-40b7-b404-bebd55ff1756");
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-08-06T06:00:00Z");

    @Mock ClientDeviceRepository repository;
    private ClientDeviceService service;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        service = new ClientDeviceService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void registersNewDevice() {
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(TENANT_ID, request(keyPair));

        assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(response.tenantId()).isEqualTo(TENANT_ID);
        verify(repository).save(any());
    }

    @Test
    void returnsExistingDeviceWhenTenantAndPublicKeyMatch() {
        var existing = ClientDeviceEntity.create(
                DEVICE_ID,
                TENANT_ID,
                "Yerel ajan",
                keyPair.getPublic().getEncoded(),
                NOW);
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));

        var response = service.register(TENANT_ID, request(keyPair));

        assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsExistingUuidWithDifferentKey() throws Exception {
        var existing = ClientDeviceEntity.create(
                DEVICE_ID,
                TENANT_ID,
                "Yerel ajan",
                keyPair.getPublic().getEncoded(),
                NOW);
        var otherKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(TENANT_ID, request(otherKeyPair)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("CLIENT_DEVICE_KEY_MISMATCH"));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsDeviceOwnedByAnotherTenant() {
        var existing = ClientDeviceEntity.create(
                DEVICE_ID,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Başka tenant ajanı",
                keyPair.getPublic().getEncoded(),
                NOW);
        when(repository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(TENANT_ID, request(keyPair)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("CLIENT_DEVICE_FORBIDDEN"));
        verify(repository, never()).save(any());
    }

    private static RegisterClientDeviceRequest request(KeyPair value) {
        return new RegisterClientDeviceRequest(
                DEVICE_ID,
                "Yerel demo ajanı",
                Base64.getEncoder().encodeToString(value.getPublic().getEncoded()));
    }
}