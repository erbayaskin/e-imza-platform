package io.github.erbayaskin.eimza.pades;

import io.github.erbayaskin.eimza.cades.CadesSigningPreparation;

public record PadesSigningPreparation(
        byte[] preparedPdf,
        CadesSigningPreparation cmsPreparation) {
    public PadesSigningPreparation {
        preparedPdf = preparedPdf.clone();
    }
    @Override public byte[] preparedPdf() { return preparedPdf.clone(); }
}
