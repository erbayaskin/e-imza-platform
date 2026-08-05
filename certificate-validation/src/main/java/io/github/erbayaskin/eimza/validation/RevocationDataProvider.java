package io.github.erbayaskin.eimza.validation;

import java.security.cert.X509Certificate;
import java.time.Instant;

public interface RevocationDataProvider {

    RevocationEvidence resolve(
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime);
}
