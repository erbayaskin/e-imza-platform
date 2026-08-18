package io.github.erbayaskin.eimza.api.serversigning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.api.error.ApiException;

class ServerPkcs11SigningServiceTest {

    @Test
    void leavesCredentialNullSoSmartCardMiddlewareCanAttemptPinlessSigning() {
        var credentials = mock(ServerCredentialProvider.class);
        var service = new ServerPkcs11SigningService(credentials);

        var resolved = service.resolveSigningCredential(smartCard(null), null);

        assertThat(resolved).isNull();
        verifyNoInteractions(credentials);
    }

    @Test
    void usesOneTimeRequestPinForSmartCardWithoutResolvingStoredCredential() {
        var credentials = mock(ServerCredentialProvider.class);
        var service = new ServerPkcs11SigningService(credentials);
        var supplied = new char[] {'1', '2', '3', '4'};

        var resolved = service.resolveSigningCredential(smartCard("CARD_PIN"), supplied);

        assertThat(resolved).containsExactly(supplied);
        assertThat(resolved).isNotSameAs(supplied);
        verifyNoInteractions(credentials);
        Arrays.fill(resolved, '\0');
        Arrays.fill(supplied, '\0');
    }

    @Test
    void resolvesOptionalSmartCardCredentialWhenRequestPinIsAbsent() {
        var credentials = mock(ServerCredentialProvider.class);
        var service = new ServerPkcs11SigningService(credentials);
        var secret = new char[] {'5', '6', '7', '8'};
        when(credentials.resolve("CARD_PIN")).thenReturn(secret);

        var resolved = service.resolveSigningCredential(smartCard("CARD_PIN"), null);

        assertThat(resolved).isSameAs(secret);
        verify(credentials).resolve("CARD_PIN");
        Arrays.fill(resolved, '\0');
    }

    @Test
    void keepsHsmCredentialResolutionAndRejectsRequestPin() {
        var credentials = mock(ServerCredentialProvider.class);
        var service = new ServerPkcs11SigningService(credentials);
        var secret = new char[] {'9', '8', '7', '6'};
        when(credentials.resolve("HSM_PIN")).thenReturn(secret);

        var resolved = service.resolveSigningCredential(hsm(), null);

        assertThat(resolved).isSameAs(secret);
        verify(credentials).resolve("HSM_PIN");
        Arrays.fill(resolved, '\0');

        assertThatThrownBy(() -> service.resolveSigningCredential(
                        hsm(), new char[] {'1'}))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("HSM_PIN_NOT_ALLOWED"));
    }

    @Test
    void mapsDeviceReportedLoginRequirementToStableSmartCardError() {
        var error = ServerPkcs11SigningService.classifiedPkcs11Error(
                smartCard(null),
                new Exception("CKR_USER_NOT_LOGGED_IN"));

        assertThat(error.code()).isEqualTo("SERVER_SMART_CARD_LOGIN_REQUIRED");
        assertThat(error.status().value()).isEqualTo(422);
    }

    @Test
    void mapsSunPkcs11MissingPasswordSignalToStableSmartCardError() {
        var error = ServerPkcs11SigningService.classifiedPkcs11Error(
                smartCard(null),
                new Exception(
                        "Login failed: no password provided, and no callback handler available for retrieving password"));

        assertThat(error.code()).isEqualTo("SERVER_SMART_CARD_LOGIN_REQUIRED");
    }

    private static ServerKeyProfile smartCard(String credentialRef) {
        return new ServerKeyProfile(
                "server-card",
                ServerDeviceType.SMART_CARD,
                Path.of("C:/Windows/System32/akisp11.dll"),
                null,
                new byte[] {0x3B},
                new byte[] {(byte) 0xFF},
                null,
                credentialRef,
                Set.of());
    }

    private static ServerKeyProfile hsm() {
        return new ServerKeyProfile(
                "server-hsm",
                ServerDeviceType.HSM,
                Path.of("C:/Program Files/HSM/vendor-pkcs11.dll"),
                4,
                null,
                null,
                null,
                "HSM_PIN",
                Set.of());
    }
}
