package io.github.erbayaskin.eimza.smartcard.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.Atr;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;
import io.github.erbayaskin.eimza.smartcard.profile.CardProfileRegistry;

@Component
public class PcscSmartCardGateway implements SmartCardGateway {

    private final CardProfileRegistry profiles;

    public PcscSmartCardGateway(CardProfileRegistry profiles) {
        this.profiles = profiles;
    }

    @Override
    public List<DiscoveredCard> discover() {
        try {
            var result = new ArrayList<DiscoveredCard>();
            for (CardTerminal terminal : TerminalFactory.getDefault().terminals().list()) {
                var readerId = readerId(terminal.getName());
                if (!terminal.isCardPresent()) {
                    result.add(new DiscoveredCard(readerId, null, false, null, null, "NO_CARD"));
                    continue;
                }
                var card = terminal.connect("*");
                try {
                    var atr = new Atr(card.getATR().getBytes());
                    try {
                        var profile = profiles.match(atr);
                        result.add(new DiscoveredCard(
                                readerId, atr.hex(), true, profile.id(), profile.displayName(), "READY"));
                    } catch (AgentException exception) {
                        result.add(new DiscoveredCard(
                                readerId, atr.hex(), true, null, null, exception.code()));
                    }
                } finally {
                    card.disconnect(false);
                }
            }
            return result;
        } catch (CardException exception) {
            throw new AgentException("PCSC_ERROR", "Akıllı kart okuyucularına erişilemedi.", exception);
        }
    }

    private static String readerId(String terminalName) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(terminalName.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 çalışma ortamında bulunamadı.", exception);
        }
    }
}
