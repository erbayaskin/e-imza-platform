package io.github.erbayaskin.eimza.api.cardprofile;

import java.util.List;

public record CardProfileTemplate(
        String id,
        String displayName,
        String vendor,
        String deviceType,
        String atr,
        String atrMask,
        String defaultPkcs11Library,
        List<String> allowedMechanisms,
        String description) {

    public CardProfileTemplate {
        allowedMechanisms = List.copyOf(allowedMechanisms);
    }
}
