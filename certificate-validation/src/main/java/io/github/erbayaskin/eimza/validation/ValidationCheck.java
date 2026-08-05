package io.github.erbayaskin.eimza.validation;

import java.util.Map;
import io.github.erbayaskin.eimza.core.model.ValidationIndication;

public record ValidationCheck(
        String code,
        ValidationIndication indication,
        String message,
        String evidenceDigest,
        Map<String, String> details) {

    public ValidationCheck {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ValidationCheck of(
            String code,
            ValidationIndication indication,
            String message) {
        return new ValidationCheck(code, indication, message, null, Map.of());
    }
}
