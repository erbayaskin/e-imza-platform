package io.github.erbayaskin.eimza.smartcard.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.erbayaskin.eimza.smartcard.device.DiscoveredCard;
import io.github.erbayaskin.eimza.smartcard.deviceidentity.AgentDeviceSigner;
import io.github.erbayaskin.eimza.smartcard.manifest.SignedManifestRequest;
import io.github.erbayaskin.eimza.smartcard.service.CardAgentService;
import io.github.erbayaskin.eimza.smartcard.service.CardAgentService.SigningResponse;
import io.github.erbayaskin.eimza.smartcard.token.CardCertificate;

@RestController
@RequestMapping("/agent/v1")
public class CardAgentController {

    private final CardAgentService service;

    public CardAgentController(CardAgentService service) {
        this.service = service;
    }

    @GetMapping("/cards")
    List<DiscoveredCard> cards() {
        return service.cards();
    }

    @GetMapping("/device")
    AgentDeviceSigner.DeviceIdentity device() {
        return service.deviceIdentity();
    }

    @GetMapping("/cards/{readerId}/certificates")
    List<CardCertificate> certificates(@PathVariable String readerId) {
        return service.cachedCertificates(readerId);
    }

    @PostMapping("/cards/{readerId}/certificates/refresh")
    List<CardCertificate> refreshCertificates(@PathVariable String readerId) {
        return service.refreshCertificates(readerId);
    }

    @PostMapping("/signing-requests")
    SigningResponse sign(@Valid @RequestBody SignedManifestRequest request) {
        return service.sign(request);
    }
}
