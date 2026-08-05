package io.github.erbayaskin.eimza.desktop;

import java.util.ArrayList;
import java.util.List;
import javax.smartcardio.CardException;
import javax.smartcardio.TerminalFactory;
import io.github.erbayaskin.eimza.smartcard.Atr;

final class OfflineCardService {

    private final OfflineCardProfileStore store;

    OfflineCardService(OfflineCardProfileStore store) {
        this.store = store;
    }

    List<DetectedCard> scan() throws Exception {
        var profiles = store.load();
        var result = new ArrayList<DetectedCard>();
        for (var terminal : TerminalFactory.getDefault().terminals().list()) {
            try {
                if (!terminal.isCardPresent()) {
                    continue;
                }
                var connection = terminal.connect("*");
                try {
                    var atr = new Atr(connection.getATR().getBytes()).hex();
                    var matched = profiles.stream()
                            .filter(profile -> profile.matches(atr))
                            .findFirst()
                            .orElse(null);
                    result.add(new DetectedCard(terminal.getName(), atr, matched));
                } finally {
                    connection.disconnect(false);
                }
            } catch (CardException exception) {
                result.add(new DetectedCard(
                        terminal.getName(), "Okunamadı: " + exception.getMessage(), null));
            }
        }
        return List.copyOf(result);
    }

    record DetectedCard(String readerName, String atr, OfflineCardProfile profile) {
        boolean matched() {
            return profile != null;
        }

        @Override
        public String toString() {
            return matched()
                    ? profile.displayName() + " — " + readerName
                    : "Tanımsız kart — " + readerName + " — ATR: " + atr;
        }
    }
}
