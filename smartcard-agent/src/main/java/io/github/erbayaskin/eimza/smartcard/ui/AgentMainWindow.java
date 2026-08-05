package io.github.erbayaskin.eimza.smartcard.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.service.CardAgentService;

@Component
public class AgentMainWindow {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentMainWindow.class);
    private final CardAgentService service;
    private final ConfigurableApplicationContext context;
    private final boolean enabled;

    public AgentMainWindow(
            CardAgentService service,
            ConfigurableApplicationContext context,
            @Value("${eimza.agent.ui.enabled:true}") boolean enabled) {
        this.service = service;
        this.context = context;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void showWhenReady() {
        if (!enabled) return;
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.warn("Grafik ortamı bulunamadığı için Smart Card Agent penceresi açılamadı.");
            return;
        }
        SwingUtilities.invokeLater(this::showWindow);
    }

    private void showWindow() {
        var identity = service.deviceIdentity();
        var frame = new JFrame("E-İmza Smart Card Agent");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(620, 390));
        frame.setLocationByPlatform(true);

        var title = new JLabel(
                "<html><h2>E-İmza Smart Card Agent çalışıyor</h2>"
                        + "<b>Yerel adres:</b> http://127.0.0.1:18443<br>"
                        + "<b>Cihaz UUID:</b> " + escape(identity.deviceId()) + "<br>"
                        + "<b>Cihaz anahtarı:</b> " + escape(identity.persistence()) + "</html>");
        title.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

        var output = new JTextArea();
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setText("Kart durumunu görmek için “Kartları yenile” düğmesine basın.\n"
                + "PIN yalnız bu masaüstü uygulamasının açtığı pencerelere girilir.");

        var refresh = new JButton("Kartları yenile");
        refresh.addActionListener(ignored -> refreshCards(refresh, output));
        var openDemo = new JButton("Demo sayfasını aç");
        openDemo.addActionListener(ignored -> openDemo());
        var close = new JButton("Agent'ı kapat");
        close.addActionListener(ignored -> shutdown(frame));

        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refresh);
        actions.add(openDemo);
        actions.add(close);

        frame.add(title, BorderLayout.NORTH);
        frame.add(new JScrollPane(output), BorderLayout.CENTER);
        frame.add(actions, BorderLayout.SOUTH);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) {
                shutdown(frame);
            }
        });
        frame.pack();
        frame.setVisible(true);
        refreshCards(refresh, output);
    }

    private void refreshCards(JButton button, JTextArea output) {
        button.setEnabled(false);
        output.setText("Kartlar taranıyor...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                var cards = service.cards();
                if (cards.isEmpty()) return "Takılı akıllı kart bulunamadı.";
                var text = new StringBuilder();
                for (var card : cards) {
                    text.append("Okuyucu: ").append(card.readerId()).append('\n')
                            .append("Profil: ").append(card.profileName()).append('\n')
                            .append("ATR: ").append(card.atr()).append('\n')
                            .append("Durum: ").append(card.status()).append("\n\n");
                }
                return text.toString();
            }
            @Override protected void done() {
                try {
                    output.setText(get());
                } catch (Exception exception) {
                    output.setText("Kart taraması başarısız: "
                            + rootMessage(exception));
                } finally {
                    button.setEnabled(true);
                }
            }
        }.execute();
    }

    private static void openDemo() {
        try {
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop API yok.");
            Desktop.getDesktop().browse(URI.create("http://localhost:8080/demo/"));
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(
                    null,
                    "Tarayıcı açılamadı. http://localhost:8080/demo/ adresini elle açın.",
                    "Demo açılamadı",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void shutdown(JFrame frame) {
        frame.dispose();
        context.close();
        System.exit(0);
    }

    private static String rootMessage(Throwable value) {
        var current = value;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
