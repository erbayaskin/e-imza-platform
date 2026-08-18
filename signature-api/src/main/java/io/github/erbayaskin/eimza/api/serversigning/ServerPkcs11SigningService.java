package io.github.erbayaskin.eimza.api.serversigning;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Component
public class ServerPkcs11SigningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerPkcs11SigningService.class);
    private static final int MAX_SLOT_PROBES = 16;
    private static final byte[] SHA256_DIGEST_INFO_PREFIX =
            HexFormat.of().parseHex("3031300D060960864801650304020105000420");
    private static final byte[] SHA384_DIGEST_INFO_PREFIX =
            HexFormat.of().parseHex("3041300D060960864801650304020205000430");
    private static final byte[] SHA512_DIGEST_INFO_PREFIX =
            HexFormat.of().parseHex("3051300D060960864801650304020305000440");

    private final ServerCredentialProvider credentials;
    private final Map<String, Provider> providers = new ConcurrentHashMap<>();
    private final Map<String, Integer> smartCardSlots = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public ServerPkcs11SigningService(ServerCredentialProvider credentials) {
        this.credentials = credentials;
    }

    public ServerTokenSignature sign(
            ServerKeyProfile profile,
            char[] suppliedSmartCardPin,
            String requestedSignatureAlgorithm,
            DigestPreparation preparation) {
        synchronized (locks.computeIfAbsent(profile.serverKeyId(), ignored -> new Object())) {
            char[] pin = null;
            PreparedPayload prepared = null;
            try {
                if (profile.deviceType() == ServerDeviceType.SMART_CARD) {
                    verifySmartCardAtr(profile);
                }
                pin = resolveSigningCredential(profile, suppliedSmartCardPin);
                var slot = selectedSlot(profile, pin);
                var keyStore = open(profile, slot, pin);
                var selected = selectKey(keyStore, profile.certificateFingerprint());
                var certificate = selected.certificate();
                var algorithm = signatureAlgorithm(certificate, requestedSignatureAlgorithm);
                prepared = preparation.prepare(certificate, algorithm);
                var privateKey = keyStore.getKey(selected.alias(), null);
                if (!(privateKey instanceof PrivateKey signingKey)) {
                    throw error(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "SERVER_PRIVATE_KEY_NOT_FOUND",
                            "Sunucu profilindeki özel anahtar bulunamadı.");
                }
                var signer = Signature.getInstance(jcaAlgorithm(algorithm), provider(profile, slot));
                signer.initSign(signingKey);
                signer.update(signingPayload(algorithm, prepared.digestToSign()));
                var raw = signer.sign();
                var encoded = certificate.getEncoded();
                return new ServerTokenSignature(
                        encoded,
                        fingerprint(encoded),
                        algorithm,
                        raw,
                        prepared.preparationJson());
            } catch (ApiException exception) {
                throw exception;
            } catch (Exception exception) {
                LOGGER.error(
                        "Server-side PKCS#11 imzalama başarısız: serverKeyId={}, deviceType={}",
                        profile.serverKeyId(),
                        profile.deviceType(),
                        exception);
                throw classifiedPkcs11Error(profile, exception);
            } finally {
                if (pin != null) Arrays.fill(pin, '\0');
                if (prepared != null) prepared.destroy();
            }
        }
    }

    char[] resolveSigningCredential(ServerKeyProfile profile, char[] suppliedSmartCardPin) {
        var supplied = suppliedSmartCardPin != null && suppliedSmartCardPin.length > 0;
        if (profile.deviceType() == ServerDeviceType.SMART_CARD) {
            if (supplied) {
                return suppliedSmartCardPin.clone();
            }
            return hasText(profile.credentialRef())
                    ? credentials.resolve(profile.credentialRef())
                    : null;
        }
        if (supplied) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "HSM_PIN_NOT_ALLOWED",
                    "HSM PIN'i API isteğinde kabul edilmez; credentialRef kullanılmalıdır.");
        }
        return credentials.resolve(profile.credentialRef());
    }

    private int selectedSlot(ServerKeyProfile profile, char[] pin) {
        if (profile.deviceType() == ServerDeviceType.HSM) {
            if (profile.slotListIndex() == null) {
                throw error(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "SERVER_PKCS11_SLOT_REQUIRED",
                        "HSM profili için PKCS#11 slot numarası zorunludur.");
            }
            return profile.slotListIndex();
        }
        var cached = smartCardSlots.get(profile.serverKeyId());
        if (cached != null) {
            return cached;
        }
        var discovered = discoverSlot(profile, pin);
        var raced = smartCardSlots.putIfAbsent(profile.serverKeyId(), discovered);
        return raced == null ? discovered : raced;
    }

    private int discoverSlot(ServerKeyProfile profile, char[] pin) {
        Integer firstTokenSlot = null;
        Exception loginRequiredFailure = null;
        for (int slot = 0; slot < MAX_SLOT_PROBES; slot++) {
            try {
                var keyStore = open(profile, slot, pin);
                if (firstTokenSlot == null) {
                    firstTokenSlot = slot;
                }
                var aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    var certificate = keyStore.getCertificate(aliases.nextElement());
                    if (certificate instanceof X509Certificate x509
                            && (profile.certificateFingerprint() == null
                                    || profile.certificateFingerprint().equals(fingerprint(x509.getEncoded())))) {
                        return slot;
                    }
                }
            } catch (Exception exception) {
                var cause = causeText(exception);
                if (cause.contains("CKR_PIN_INCORRECT")
                        || cause.contains("LOGIN FAILED")
                        || cause.contains("CKR_PIN_LOCKED")) {
                    throw classifiedPkcs11Error(profile, exception);
                }
                if (loginRequired(cause) && loginRequiredFailure == null) {
                    loginRequiredFailure = exception;
                }
                // Empty/unavailable slots are expected while probing a smart card driver.
                LOGGER.debug(
                        "PKCS#11 slot adayı kullanılamıyor: serverKeyId={}, slotListIndex={}, cause={}",
                        profile.serverKeyId(),
                        slot,
                        cause);
            }
        }
        if (firstTokenSlot != null && profile.certificateFingerprint() == null) {
            LOGGER.info(
                    "İlk erişilebilir token slotu seçildi: serverKeyId={}, slotListIndex={}",
                    profile.serverKeyId(),
                    firstTokenSlot);
            return firstTokenSlot;
        }
        if (profile.slotListIndex() != null) {
            LOGGER.warn(
                    "PKCS#11 slotu sertifika ile eşleştirilemedi; yapılandırılmış fallback kullanılıyor: serverKeyId={}, slotListIndex={}",
                    profile.serverKeyId(),
                    profile.slotListIndex());
            return profile.slotListIndex();
        }
        if (loginRequiredFailure != null) {
            throw classifiedPkcs11Error(profile, loginRequiredFailure);
        }
        throw error(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "SERVER_PKCS11_SLOT_NOT_FOUND",
                "Akıllı kartın PKCS#11 token slotu otomatik bulunamadı.");
    }

    private KeyStore open(ServerKeyProfile profile, int slot, char[] pin) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS11", provider(profile, slot));
        keyStore.load(null, pin);
        return keyStore;
    }

    private Provider provider(ServerKeyProfile profile, int slot) {
        return providers.computeIfAbsent(
                profile.serverKeyId() + ":" + slot,
                ignored -> configureProvider(profile, slot));
    }

    private Provider configureProvider(ServerKeyProfile profile, int slot) {
        if (!Files.isRegularFile(profile.pkcs11Library())) {
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVER_PKCS11_LIBRARY_NOT_FOUND",
                    "Sunucu PKCS#11 sürücü kitaplığı bulunamadı.");
        }
        Path config = null;
        try {
            config = Files.createTempFile("eimza-server-pkcs11-", ".cfg");
            var safeName = profile.serverKeyId().replaceAll("[^A-Za-z0-9_-]", "_");
            Files.writeString(
                    config,
                    "name=EImzaServer_" + safeName + "_" + slot + System.lineSeparator()
                            + "library=" + profile.pkcs11Library() + System.lineSeparator()
                            + "slotListIndex=" + slot + System.lineSeparator());
            var base = Security.getProvider("SunPKCS11");
            if (base == null) {
                throw error(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVER_PKCS11_UNAVAILABLE",
                        "Java SunPKCS11 sağlayıcısı kullanılamıyor.");
            }
            var configured = base.configure(config.toString());
            Security.addProvider(configured);
            return configured;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVER_PKCS11_CONFIGURATION_FAILED",
                    "Sunucu PKCS#11 sağlayıcısı yüklenemedi.");
        } finally {
            if (config != null) {
                try {
                    Files.deleteIfExists(config);
                } catch (Exception ignored) {
                    config.toFile().deleteOnExit();
                }
            }
        }
    }

    private static SelectedKey selectKey(KeyStore keyStore, String expectedFingerprint)
            throws Exception {
        SelectedKey selected = null;
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            var alias = aliases.nextElement();
            var certificate = keyStore.getCertificate(alias);
            if (certificate instanceof X509Certificate x509 && keyStore.isKeyEntry(alias)) {
                var fingerprint = fingerprint(x509.getEncoded());
                if (expectedFingerprint != null && !expectedFingerprint.equals(fingerprint)) {
                    continue;
                }
                if (selected != null && expectedFingerprint == null) {
                    throw error(
                            HttpStatus.CONFLICT,
                            "SERVER_KEY_AMBIGUOUS",
                            "Profille eşleşen birden fazla özel anahtar var; sertifika parmak izi tanımlanmalıdır.");
                }
                selected = new SelectedKey(alias, x509);
            }
        }
        if (selected == null) {
            throw error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SERVER_KEY_CERTIFICATE_NOT_FOUND",
                    "Sunucu profilindeki imza sertifikası bulunamadı.");
        }
        return selected;
    }

    private static void verifySmartCardAtr(ServerKeyProfile profile) {
        try {
            for (CardTerminal terminal : TerminalFactory.getDefault().terminals().list()) {
                if (!terminal.isCardPresent()) continue;
                var card = terminal.connect("*");
                try {
                    if (matches(card.getATR().getBytes(), profile.atr(), profile.atrMask())) return;
                } finally {
                    card.disconnect(false);
                }
            }
        } catch (Exception exception) {
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVER_SMART_CARD_DISCOVERY_FAILED",
                    "Sunucu akıllı kart okuyucuları taranamadı.");
        }
        throw error(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "SERVER_SMART_CARD_NOT_FOUND",
                "Sunucuda profile uygun ATR değerine sahip akıllı kart bulunamadı.");
    }

    private static boolean matches(byte[] actual, byte[] expected, byte[] mask) {
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if ((actual[i] & mask[i]) != (expected[i] & mask[i])) return false;
        }
        return true;
    }

    private static String signatureAlgorithm(
            X509Certificate certificate, String requestedSignatureAlgorithm) {
        var keyAlgorithm = certificate.getPublicKey().getAlgorithm();
        var selected = requestedSignatureAlgorithm == null
                ? switch (keyAlgorithm) {
                    case "RSA" -> "RSA_PKCS1_SHA256";
                    case "EC", "ECDSA" -> "ECDSA_SHA256";
                    default -> throw error(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "SERVER_KEY_ALGORITHM_NOT_SUPPORTED",
                            "Sunucu anahtar algoritması desteklenmiyor.");
                }
                : requestedSignatureAlgorithm;
        var allowed = switch (selected) {
            case "RSA_PKCS1_SHA256", "RSA_PKCS1_SHA384", "RSA_PKCS1_SHA512" ->
                    "RSA".equalsIgnoreCase(keyAlgorithm);
            case "ECDSA_SHA256", "ECDSA_SHA384", "ECDSA_SHA512" ->
                    "EC".equalsIgnoreCase(keyAlgorithm) || "ECDSA".equalsIgnoreCase(keyAlgorithm);
            default -> false;
        };
        if (!allowed) {
            throw error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SIGNATURE_KEY_MISMATCH",
                    "Seçilen imza algoritması sunucu anahtarının türüyle eşleşmiyor.");
        }
        return selected;
    }

    private static String jcaAlgorithm(String algorithm) {
        return algorithm.startsWith("RSA_") ? "NONEwithRSA" : "NONEwithECDSA";
    }

    private static byte[] signingPayload(String algorithm, byte[] digest) {
        var expectedLength = switch (algorithm) {
            case "RSA_PKCS1_SHA256", "ECDSA_SHA256" -> 32;
            case "RSA_PKCS1_SHA384", "ECDSA_SHA384" -> 48;
            case "RSA_PKCS1_SHA512", "ECDSA_SHA512" -> 64;
            default -> throw error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "ALGORITHM_NOT_ALLOWED",
                    "Desteklenmeyen imza algoritması.");
        };
        if (digest.length != expectedLength) {
            throw error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_DIGEST",
                    "Seçilen imza algoritması için özet " + expectedLength + " bayt olmalıdır.");
        }
        if (algorithm.startsWith("ECDSA_")) return digest.clone();
        var prefix = switch (algorithm) {
            case "RSA_PKCS1_SHA256" -> SHA256_DIGEST_INFO_PREFIX;
            case "RSA_PKCS1_SHA384" -> SHA384_DIGEST_INFO_PREFIX;
            case "RSA_PKCS1_SHA512" -> SHA512_DIGEST_INFO_PREFIX;
            default -> throw error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "ALGORITHM_NOT_ALLOWED",
                    "Desteklenmeyen imza algoritması.");
        };
        var value = new byte[prefix.length + digest.length];
        System.arraycopy(prefix, 0, value, 0, prefix.length);
        System.arraycopy(digest, 0, value, prefix.length, digest.length);
        return value;
    }

    private static String fingerprint(byte[] certificate) throws Exception {
        return HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate));
    }

    private static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message, false);
    }

    static ApiException classifiedPkcs11Error(
            ServerKeyProfile profile, Exception exception) {
        var text = causeText(exception);
        if (!loginRequired(text)
                && (text.contains("CKR_PIN_INCORRECT") || text.contains("LOGIN FAILED"))) {
            return error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SERVER_SMART_CARD_PIN_INCORRECT",
                    "Akıllı kart PIN'i kabul edilmedi. Kartın kalan PIN deneme sayısını gözeterek tekrar deneyin.");
        }
        if (text.contains("CKR_PIN_LOCKED")) {
            return error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SERVER_SMART_CARD_PIN_LOCKED",
                    "Akıllı kart PIN'i kilitli.");
        }
        if (text.contains("CKR_TOKEN_NOT_PRESENT") || text.contains("TOKEN NOT PRESENT")) {
            return error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SERVER_PKCS11_TOKEN_NOT_PRESENT",
                    "PKCS#11 token/kart seçilen slotta bulunamadı.");
        }
        if (loginRequired(text)) {
            if (profile.deviceType() == ServerDeviceType.SMART_CARD) {
                return error(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "SERVER_SMART_CARD_LOGIN_REQUIRED",
                        "Akıllı kart middleware/token oturumu özel anahtar işlemi için giriş gerektiriyor. "
                                + "İstek PIN'i opsiyoneldir; bu cihaz için tek kullanımlık PIN verin "
                                + "veya server key profilinde güvenli credentialRef yapılandırın.");
            }
            return error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SERVER_PKCS11_LOGIN_REQUIRED",
                    "PKCS#11 token özel anahtar işlemi için oturum açılmasını gerektiriyor.");
        }
        if (text.contains("CKR_MECHANISM_INVALID") || text.contains("CKR_MECHANISM_PARAM_INVALID")) {
            return error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SERVER_PKCS11_MECHANISM_NOT_SUPPORTED",
                    "Kart seçilen imza mekanizmasını desteklemiyor.");
        }
        if (text.contains("CERTIFICATEEXPIREDEXCEPTION")
                || text.contains("CERTIFICATE EXPIRED")
                || text.contains("NOTAFTER")) {
            return error(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SERVER_SIGNER_CERTIFICATE_EXPIRED",
                    "Karttaki imza sertifikasının geçerlilik süresi dolmuş; yeni imza oluşturulamaz.");
        }
        return error(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "SERVER_PKCS11_SIGNING_FAILED",
                "Sunucu PKCS#11 imzalama işlemi tamamlanamadı. Ayrıntı sunucu günlüğüne correlationId ile kaydedildi.");
    }

    private static boolean loginRequired(String text) {
        return text.contains("CKR_USER_NOT_LOGGED_IN")
                || text.contains("LOGIN REQUIRED")
                || text.contains("NO PASSWORD PROVIDED")
                || text.contains("PASSWORD MUST NOT BE NULL")
                || text.contains("CALLBACK HANDLER AVAILABLE FOR RETRIEVING PASSWORD");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String causeText(Throwable exception) {
        var result = new StringBuilder();
        var current = exception;
        while (current != null && result.length() < 4096) {
            if (current.getClass().getName() != null) {
                result.append(current.getClass().getName()).append(' ');
            }
            if (current.getMessage() != null) {
                result.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return result.toString().toUpperCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface DigestPreparation {
        PreparedPayload prepare(X509Certificate certificate, String signatureAlgorithm)
                throws Exception;
    }

    public record PreparedPayload(byte[] digestToSign, String preparationJson) {
        public PreparedPayload {
            digestToSign = digestToSign.clone();
        }

        @Override
        public byte[] digestToSign() { return digestToSign.clone(); }

        public void destroy() {
            Arrays.fill(digestToSign, (byte) 0);
        }
    }

    public record ServerTokenSignature(
            byte[] certificate,
            String certificateFingerprint,
            String signatureAlgorithm,
            byte[] rawSignature,
            String preparationJson) {
        public ServerTokenSignature {
            certificate = certificate.clone();
            rawSignature = rawSignature.clone();
        }

        @Override
        public byte[] certificate() { return certificate.clone(); }

        @Override
        public byte[] rawSignature() { return rawSignature.clone(); }
    }

    private record SelectedKey(String alias, X509Certificate certificate) {}
}
