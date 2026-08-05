package io.github.erbayaskin.eimza.api.cardprofile;

import java.util.HexFormat;
import java.util.List;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import org.springframework.stereotype.Service;

@Service
public class CardProfileCatalogService {

    private static final List<CardProfileTemplate> TEMPLATES = List.of(
            new CardProfileTemplate(
                    "akis-smart-card",
                    "AKİS Akıllı Kart",
                    "TÜBİTAK BİLGEM",
                    "SMART_CARD",
                    "3B9F978131FE4580655443D3228231C073F621808105D3",
                    "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                    "C:/Windows/System32/akisp11.dll",
                    List.of(
                            "RSA_PKCS1_SHA256",
                            "RSA_PKCS1_SHA384",
                            "RSA_PKCS1_SHA512"),
                    "AKİS PKCS#11 sürücüsü ve ATR üzerinden otomatik slot keşfi."),
            new CardProfileTemplate(
                    "custom-smart-card",
                    "Özel Akıllı Kart",
                    "Kullanıcı tanımlı",
                    "SMART_CARD",
                    null,
                    null,
                    null,
                    List.of(
                            "RSA_PKCS1_SHA256",
                            "RSA_PKCS1_SHA384",
                            "RSA_PKCS1_SHA512",
                            "ECDSA_SHA256",
                            "ECDSA_SHA384",
                            "ECDSA_SHA512"),
                    "Katalogda bulunmayan kartlar için ATR ve sürücü yolu elle girilir."));

    public List<CardProfileTemplate> templates() {
        return TEMPLATES;
    }

    public List<DetectedSmartCard> detectCards() {
        try {
            return TerminalFactory.getDefault().terminals().list().stream()
                    .map(this::detect)
                    .toList();
        } catch (Exception exception) {
            return List.of(new DetectedSmartCard(
                    null, false, null, null, null, "PCSC_UNAVAILABLE: " + safeMessage(exception)));
        }
    }

    CardProfileTemplate matchAtr(String atr) {
        if (atr == null || atr.isBlank()) {
            return null;
        }
        return TEMPLATES.stream()
                .filter(template -> template.atr() != null)
                .filter(template -> atrMatches(atr, template.atr(), template.atrMask()))
                .findFirst()
                .orElse(null);
    }

    private DetectedSmartCard detect(CardTerminal terminal) {
        try {
            if (!terminal.isCardPresent()) {
                return new DetectedSmartCard(
                        terminal.getName(), false, null, null, null, "NO_CARD");
            }
            var card = terminal.connect("*");
            try {
                var atr = HexFormat.of().withUpperCase().formatHex(card.getATR().getBytes());
                var template = matchAtr(atr);
                return new DetectedSmartCard(
                        terminal.getName(),
                        true,
                        atr,
                        template == null ? null : template.id(),
                        template == null ? null : template.displayName(),
                        template == null ? "UNKNOWN_CARD" : "MATCHED");
            } finally {
                card.disconnect(false);
            }
        } catch (Exception exception) {
            return new DetectedSmartCard(
                    terminal.getName(), true, null, null, null, "READ_ERROR: " + safeMessage(exception));
        }
    }

    static boolean atrMatches(String actualValue, String expectedValue, String maskValue) {
        try {
            var actual = HexFormat.of().parseHex(normalize(actualValue));
            var expected = HexFormat.of().parseHex(normalize(expectedValue));
            var mask = HexFormat.of().parseHex(normalize(maskValue));
            if (actual.length != expected.length || actual.length != mask.length) {
                return false;
            }
            for (int i = 0; i < actual.length; i++) {
                if ((actual[i] & mask[i]) != (expected[i] & mask[i])) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
