package io.github.erbayaskin.eimza.smartcard.token;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.CardProfile;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

@Component
public class Pkcs11TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(Pkcs11TokenService.class);
    private static final int MAXIMUM_SLOT_PROBE_COUNT = 16;
    private final Map<String, Provider> providers = new ConcurrentHashMap<>();
    private final Map<String, Integer> resolvedSlots = new ConcurrentHashMap<>();
    private final PublicPkcs11CertificateReader publicCertificateReader;

    public Pkcs11TokenService(PublicPkcs11CertificateReader publicCertificateReader) {
        this.publicCertificateReader = publicCertificateReader;
    }

    public List<CardCertificate> certificates(CardProfile profile, char[] pin) {
        if (pin == null) {
            return publicCertificateReader.read(profile);
        }
        try {
            var keyStore = open(profile, pin, null);
            var result = new ArrayList<CardCertificate>();
            var aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                var alias = aliases.nextElement();
                var certificate = keyStore.getCertificate(alias);
                if (certificate instanceof X509Certificate x509) {
                    var hasPrivateKey = keyStore.isKeyEntry(alias);
                    result.add(new CardCertificate(
                            fingerprint(x509),
                            x509.getSubjectX500Principal().getName(),
                            x509.getIssuerX500Principal().getName(),
                            x509.getSerialNumber().toString(16).toUpperCase(),
                            x509.getNotBefore().toInstant(),
                            x509.getNotAfter().toInstant(),
                            x509.getPublicKey().getAlgorithm(),
                            hasPrivateKey,
                            java.util.Base64.getEncoder().encodeToString(x509.getEncoded())));
                }
            }
            return result.stream()
                    .sorted(Comparator.comparing(CardCertificate::fingerprintSha256))
                    .toList();
        } catch (AgentException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error(
                    "PKCS#11 sertifika okuma başarısız: profileId={}",
                    profile.id(),
                    exception);
            throw tokenError(exception);
        }
    }

    public byte[] sign(
            CardProfile profile,
            String certificateFingerprint,
            byte[] digest,
            String signatureAlgorithm,
            char[] pin) {
        validateDigestLength(signatureAlgorithm, digest);
        if (!profile.allowedMechanisms().contains(signatureAlgorithm)) {
            throw new AgentException("MECHANISM_NOT_ALLOWED", "İstenen imza mekanizmasına kart profilinde izin verilmemiş.");
        }
        try {
            var keyStore = open(profile, pin, certificateFingerprint);
            String selectedAlias = null;
            var aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                var alias = aliases.nextElement();
                var certificate = keyStore.getCertificate(alias);
                if (certificate instanceof X509Certificate x509
                        && keyStore.isKeyEntry(alias)
                        && fingerprint(x509).equalsIgnoreCase(certificateFingerprint)) {
                    selectedAlias = alias;
                    break;
                }
            }
            if (selectedAlias == null) {
                throw new AgentException("CERTIFICATE_NOT_FOUND", "Seçilen sertifika kartta bulunamadı.");
            }
            var key = keyStore.getKey(selectedAlias, null);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new AgentException("PRIVATE_KEY_NOT_FOUND", "Sertifikaya ait özel anahtar kartta bulunamadı.");
            }
            var signature = Signature.getInstance(
                    RawDigestSigningSupport.jcaAlgorithm(signatureAlgorithm),
                    provider(profile, selectedSlot(profile, certificateFingerprint, pin)));
            signature.initSign(privateKey);
            signature.update(RawDigestSigningSupport.payload(signatureAlgorithm, digest));
            return signature.sign();
        } catch (AgentException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error(
                    "PKCS#11 imzalama başarısız: profileId={}, algorithm={}",
                    profile.id(),
                    signatureAlgorithm,
                    exception);
            throw tokenError(exception);
        }
    }

    private KeyStore open(
            CardProfile profile, char[] pin, String certificateFingerprint) throws Exception {
        var slot = selectedSlot(profile, certificateFingerprint, pin);
        try {
            return openAt(profile, slot, pin);
        } catch (Exception exception) {
            if (!profile.autoDiscoverSlot() || !tokenUnavailable(exception)) {
                throw exception;
            }
            resolvedSlots.remove(profile.id(), slot);
            var rediscovered = selectedSlot(profile, certificateFingerprint, pin);
            return openAt(profile, rediscovered, pin);
        }
    }

    private KeyStore openAt(CardProfile profile, int slot, char[] pin) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS11", provider(profile, slot));
        keyStore.load(null, pin);
        return keyStore;
    }

    private int selectedSlot(
            CardProfile profile, String certificateFingerprint, char[] pin) {
        if (!profile.autoDiscoverSlot()) {
            return profile.slotListIndex();
        }
        var cached = resolvedSlots.get(profile.id());
        if (cached != null) {
            return cached;
        }
        var discovered = discoverSlot(profile, certificateFingerprint, pin);
        var raced = resolvedSlots.putIfAbsent(profile.id(), discovered);
        return raced == null ? discovered : raced;
    }

    private int discoverSlot(
            CardProfile profile, String certificateFingerprint, char[] pin) {
        Integer firstToken = null;
        for (int slot = 0; slot < MAXIMUM_SLOT_PROBE_COUNT; slot++) {
            try {
                var keyStore = openAt(profile, slot, pin);
                if (firstToken == null) {
                    firstToken = slot;
                }
                var aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    var certificate = keyStore.getCertificate(aliases.nextElement());
                    if (certificate instanceof X509Certificate x509
                            && (certificateFingerprint == null
                                    || fingerprint(x509).equalsIgnoreCase(certificateFingerprint))) {
                        LOGGER.info(
                                "PKCS#11 token slotu otomatik bulundu: profileId={}, slotListIndex={}",
                                profile.id(),
                                slot);
                        return slot;
                    }
                }
            } catch (Exception exception) {
                var cause = exceptionChain(exception).toUpperCase();
                if (cause.contains("CKR_PIN_INCORRECT")
                        || cause.contains("LOGIN FAILED")
                        || cause.contains("CKR_PIN_LOCKED")) {
                    throw tokenError(exception);
                }
                // Empty slots and indices beyond the provider slot list are expected while probing.
                LOGGER.debug(
                        "PKCS#11 slot adayi kullanilamiyor: profileId={}, slotListIndex={}, cause={}",
                        profile.id(),
                        slot,
                        cause);
            }
        }
        if (firstToken != null && certificateFingerprint == null) {
            LOGGER.info(
                    "Sertifika giris gerektiriyor; ilk dolu PKCS#11 slotu secildi: profileId={}, slotListIndex={}",
                    profile.id(),
                    firstToken);
            return firstToken;
        }
        LOGGER.warn(
                "PKCS#11 slotu PIN kullanmadan bulunamadi; yapilandirilmis fallback kullaniliyor: profileId={}, slotListIndex={}",
                profile.id(),
                profile.slotListIndex());
        return profile.slotListIndex();
    }

    private static void validateDigestLength(String signatureAlgorithm, byte[] digest) {
        var expectedLength = switch (signatureAlgorithm) {
            case "RSA_PKCS1_SHA256", "ECDSA_SHA256" -> 32;
            case "RSA_PKCS1_SHA384", "ECDSA_SHA384" -> 48;
            case "RSA_PKCS1_SHA512", "ECDSA_SHA512" -> 64;
            default -> throw new AgentException(
                    "MECHANISM_NOT_ALLOWED", "Desteklenmeyen imza mekanizması.");
        };
        if (digest.length != expectedLength) {
            throw new AgentException(
                    "INVALID_DIGEST",
                    "Seçilen imza algoritması için özet "
                            + expectedLength
                            + " bayt olmalıdır.");
        }
    }

    private Provider provider(CardProfile profile, int slotListIndex) {
        var key = profile.id() + ":" + slotListIndex;
        return providers.computeIfAbsent(
                key, ignored -> configureProvider(profile, slotListIndex));
    }

    private Provider configureProvider(CardProfile profile, int slotListIndex) {
        var library = profile.pkcs11Library().toAbsolutePath().normalize();
        if (!Files.isRegularFile(library)) {
            throw new AgentException("PKCS11_LIBRARY_NOT_FOUND", "Yapılandırılmış PKCS#11 kitaplığı bulunamadı.");
        }
        Path config = null;
        try {
            config = Files.createTempFile("eimza-pkcs11-", ".cfg");
            var safeName = profile.id().replaceAll("[^A-Za-z0-9_-]", "_");
            Files.writeString(config,
                    "name=EImza_" + safeName + System.lineSeparator()
                            + "library=" + library + System.lineSeparator()
                            + "slotListIndex=" + slotListIndex + System.lineSeparator());
            var base = Security.getProvider("SunPKCS11");
            if (base == null) {
                throw new AgentException("PKCS11_UNAVAILABLE", "Java PKCS#11 sağlayıcısı kullanılamıyor.");
            }
            var configured = base.configure(config.toString());
            Security.addProvider(configured);
            return configured;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AgentException agentException) {
                throw agentException;
            }
            throw new AgentException("PKCS11_CONFIGURATION_ERROR", "PKCS#11 sağlayıcısı yüklenemedi.", exception);
        } finally {
            if (config != null) {
                try {
                    Files.deleteIfExists(config);
                } catch (IOException ignored) {
                    config.toFile().deleteOnExit();
                }
            }
        }
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        return HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static AgentException tokenError(Exception exception) {
        var messages = exceptionChain(exception).toUpperCase();
        if (messages.contains("CKR_PIN_INCORRECT")) {
            return new AgentException("PIN_INCORRECT", "Kart PIN'i hatalı.", exception);
        }
        if (messages.contains("CKR_PIN_LOCKED")) {
            return new AgentException("PIN_LOCKED", "Kart PIN'i kilitli.", exception);
        }
        if (messages.contains("CKR_TOKEN_NOT_PRESENT") || messages.contains("CKR_DEVICE_REMOVED")) {
            return new AgentException("CARD_REMOVED", "Kart çıkarıldı veya artık erişilebilir değil.", exception);
        }
        if (messages.contains("CKR_USER_NOT_LOGGED_IN")) {
            return new AgentException(
                    "PKCS11_LOGIN_REQUIRED",
                    "Kart sertifikalarına erişmek için PKCS#11 oturumu açılamadı.",
                    exception);
        }
        if (messages.contains("CKR_SLOT_ID_INVALID")) {
            return new AgentException(
                    "PKCS11_SLOT_NOT_FOUND",
                    "Akıllı kartın PKCS#11 token slotu bulunamadı.",
                    exception);
        }
        return new AgentException("PKCS11_ERROR", "Akıllı kart işlemi tamamlanamadı.", exception);
    }

    private static boolean tokenUnavailable(Throwable exception) {
        var messages = exceptionChain(exception).toUpperCase();
        return messages.contains("CKR_TOKEN_NOT_PRESENT")
                || messages.contains("CKR_DEVICE_REMOVED")
                || messages.contains("TOKEN NOT PRESENT");
    }

    private static String exceptionChain(Throwable throwable) {
        var result = new StringBuilder();
        for (var current = throwable; current != null; current = current.getCause()) {
            result.append(' ').append(current.getMessage());
        }
        return result.toString();
    }
}
