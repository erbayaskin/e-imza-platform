package io.github.erbayaskin.eimza.smartcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import io.github.erbayaskin.eimza.smartcard.CardProfile;
import io.github.erbayaskin.eimza.smartcard.device.DiscoveredCard;
import io.github.erbayaskin.eimza.smartcard.device.SmartCardGateway;
import io.github.erbayaskin.eimza.smartcard.deviceidentity.AgentDeviceSigner;
import io.github.erbayaskin.eimza.smartcard.manifest.ManifestVerifier;
import io.github.erbayaskin.eimza.smartcard.pin.PinProvider;
import io.github.erbayaskin.eimza.smartcard.profile.CardProfileRegistry;
import io.github.erbayaskin.eimza.smartcard.token.CardCertificate;
import io.github.erbayaskin.eimza.smartcard.token.Pkcs11TokenService;

class CardAgentServiceTest {

    @Test
    void readsPublicCertificatesWithoutRequestingPin() {
        var cards = mock(SmartCardGateway.class);
        var profiles = mock(CardProfileRegistry.class);
        var pinProvider = mock(PinProvider.class);
        var tokens = mock(Pkcs11TokenService.class);
        var profile = mock(CardProfile.class);
        var certificate = mock(CardCertificate.class);
        when(cards.discover()).thenReturn(List.of(new DiscoveredCard(
                "reader-1", "3B00", true, "akis", "AKİS", "READY")));
        when(profiles.byId("akis")).thenReturn(profile);
        when(tokens.certificates(profile, null)).thenReturn(List.of(certificate));
        var service = new CardAgentService(
                cards,
                profiles,
                pinProvider,
                tokens,
                mock(ManifestVerifier.class),
                mock(AgentDeviceSigner.class));

        var result = service.refreshCertificates("reader-1");

        assertThat(result).containsExactly(certificate);
        verify(tokens).certificates(org.mockito.ArgumentMatchers.eq(profile), isNull());
        verifyNoInteractions(pinProvider);
    }
}
