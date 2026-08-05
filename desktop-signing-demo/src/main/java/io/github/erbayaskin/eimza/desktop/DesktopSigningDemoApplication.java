package io.github.erbayaskin.eimza.desktop;

import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import io.github.erbayaskin.eimza.smartcard.pin.SwingPinProvider;
import io.github.erbayaskin.eimza.smartcard.token.AkisCifPublicCertificateReader;
import io.github.erbayaskin.eimza.smartcard.token.Pkcs11TokenService;
import io.github.erbayaskin.eimza.smartcard.token.PublicPkcs11CertificateReader;

public final class DesktopSigningDemoApplication {

    private DesktopSigningDemoApplication() {}

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SwingUtilities.invokeLater(() -> {
            try {
                var store = new OfflineCardProfileStore(dataDirectory(args));
                var publicReader =
                        new PublicPkcs11CertificateReader(new AkisCifPublicCertificateReader());
                var tokens = new Pkcs11TokenService(publicReader);
                var cardService = new OfflineCardService(store);
                new DesktopSigningFrame(store, cardService, tokens, new SwingPinProvider()).show();
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(
                        null,
                        "Masaüstü uygulaması başlatılamadı: " + exception.getMessage(),
                        "Başlatma hatası",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    static Path dataDirectory(String[] args) {
        for (var argument : args) {
            if (argument.startsWith("--data-dir=")) {
                return Path.of(argument.substring("--data-dir=".length()))
                        .toAbsolutePath()
                        .normalize();
            }
        }
        return Path.of(".eimza-desktop").toAbsolutePath().normalize();
    }
}
