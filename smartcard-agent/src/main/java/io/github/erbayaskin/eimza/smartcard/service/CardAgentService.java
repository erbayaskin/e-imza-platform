package io.github.erbayaskin.eimza.smartcard.service;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import io.github.erbayaskin.eimza.smartcard.device.DiscoveredCard;
import io.github.erbayaskin.eimza.smartcard.deviceidentity.AgentDeviceSigner;
import io.github.erbayaskin.eimza.smartcard.device.SmartCardGateway;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;
import io.github.erbayaskin.eimza.smartcard.manifest.ManifestVerifier;
import io.github.erbayaskin.eimza.smartcard.manifest.SignedManifestRequest;
import io.github.erbayaskin.eimza.smartcard.pin.PinProvider;
import io.github.erbayaskin.eimza.smartcard.profile.CardProfileRegistry;
import io.github.erbayaskin.eimza.smartcard.token.CardCertificate;
import io.github.erbayaskin.eimza.smartcard.token.Pkcs11TokenService;

@Service
public class CardAgentService {

    private final SmartCardGateway cards;
    private final CardProfileRegistry profiles;
    private final PinProvider pinProvider;
    private final Pkcs11TokenService tokenService;
    private final ManifestVerifier manifestVerifier;
    private final AgentDeviceSigner deviceSigner;
    private final Map<String, List<CardCertificate>> certificateCache = new ConcurrentHashMap<>();

    public CardAgentService(
            SmartCardGateway cards,
            CardProfileRegistry profiles,
            PinProvider pinProvider,
            Pkcs11TokenService tokenService,
            ManifestVerifier manifestVerifier,
            AgentDeviceSigner deviceSigner) {
        this.cards = cards;
        this.profiles = profiles;
        this.pinProvider = pinProvider;
        this.tokenService = tokenService;
        this.manifestVerifier = manifestVerifier;
        this.deviceSigner = deviceSigner;
    }

    public List<DiscoveredCard> cards() {
        return cards.discover();
    }

    public List<CardCertificate> cachedCertificates(String readerId) {
        return certificateCache.getOrDefault(readerId, List.of());
    }

    public List<CardCertificate> refreshCertificates(String readerId) {
        var card = readyCard(readerId);
        var profile = profiles.byId(card.profileId());
        var certificates = tokenService.certificates(profile, null);
        if (certificates.isEmpty()) {
            throw new AgentException(
                    "CERTIFICATE_NOT_FOUND",
                    "Kartta public X.509 sertifikası bulunamadı.");
        }
        certificateCache.put(readerId, certificates);
        return certificates;
    }

    public SigningResponse sign(SignedManifestRequest request) {
        var manifest = manifestVerifier.verify(request);
        var card = readyCard(manifest.readerId());
        var profile = profiles.byId(card.profileId());
        byte[] digest;
        try {
            digest = Base64.getUrlDecoder().decode(manifest.digest());
        } catch (IllegalArgumentException exception) {
            throw new AgentException("INVALID_DIGEST", "Özet Base64URL kodlaması geçersiz.", exception);
        }
        var pin = pinProvider.requestPin(card.profileName(), "imzalama");
        try {
            var signature = tokenService.sign(
                    profile,
                    manifest.certificateFingerprint(),
                    digest,
                    manifest.signatureAlgorithm(),
                    pin);
            var encodedSignature =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            return new SigningResponse(
                    manifest.sessionId(),
                    manifest.certificateFingerprint(),
                    manifest.signatureAlgorithm(),
                    encodedSignature,
                    deviceSigner.identity().deviceId(),
                    deviceSigner.sign(
                            manifest.sessionId(),
                            manifest.nonce(),
                            manifest.certificateFingerprint(),
                            manifest.signatureAlgorithm(),
                            encodedSignature));
        } finally {
            Arrays.fill(pin, '\0');
            Arrays.fill(digest, (byte) 0);
        }
    }

    private DiscoveredCard readyCard(String readerId) {
        return cards.discover().stream()
                .filter(item -> item.readerId().equals(readerId))
                .findFirst()
                .map(card -> {
                    if (!card.cardPresent()) {
                        throw new AgentException("CARD_REMOVED", "Okuyucuda kart bulunmuyor.");
                    }
                    if (!"READY".equals(card.status())) {
                        throw new AgentException(card.status(), "Kart kullanıma hazır değil.");
                    }
                    return card;
                })
                .orElseThrow(() -> new AgentException("READER_NOT_FOUND", "Kart okuyucu bulunamadı."));
    }

    public record SigningResponse(
            String sessionId,
            String certificateFingerprint,
            String signatureAlgorithm,
            String signature,
            String deviceId,
            String deviceSignature) {}

    public AgentDeviceSigner.DeviceIdentity deviceIdentity() {
        return deviceSigner.identity();
    }
}
