package io.github.erbayaskin.eimza.api.signing;

import jakarta.validation.constraints.Size;
import java.util.Arrays;

public record ServerSideSigningRequest(@Size(min = 1, max = 128) char[] pin) {
    public ServerSideSigningRequest {
        pin = pin == null ? null : pin.clone();
    }

    @Override
    public char[] pin() {
        return pin == null ? null : pin.clone();
    }

    public void destroy() {
        if (pin != null) Arrays.fill(pin, '\0');
    }
}
