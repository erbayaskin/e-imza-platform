package io.github.erbayaskin.eimza.xades;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

public record XadesVerificationResult(
        String format,
        String level,
        X509Certificate signerCertificate,
        List<X509Certificate> embeddedCertificates,
        Instant signingTime,
        Instant timestampGenerationTime) {

    public XadesVerificationResult {
        embeddedCertificates = List.copyOf(embeddedCertificates);
    }
}
