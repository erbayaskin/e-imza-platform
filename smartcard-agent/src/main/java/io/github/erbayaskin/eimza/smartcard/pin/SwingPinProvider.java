package io.github.erbayaskin.eimza.smartcard.pin;

import java.awt.GraphicsEnvironment;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

@Component
public class SwingPinProvider implements PinProvider {

    @Override
    public char[] requestPin(String cardName, String operation) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new AgentException("PIN_UI_UNAVAILABLE", "PIN penceresi bu çalışma ortamında açılamıyor.");
        }
        var password = new JPasswordField(18);
        var panel = new JPanel();
        panel.add(new JLabel(cardName + " — " + operation + " PIN:"));
        panel.add(password);
        var option = JOptionPane.showConfirmDialog(
                null, panel, "E-İmza PIN", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            throw new AgentException("PIN_CANCELLED", "PIN girişi kullanıcı tarafından iptal edildi.");
        }
        var pin = password.getPassword();
        password.setText("");
        if (pin.length == 0) {
            throw new AgentException("PIN_REQUIRED", "PIN boş bırakılamaz.");
        }
        return pin;
    }
}
