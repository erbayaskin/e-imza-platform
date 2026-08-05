package io.github.erbayaskin.eimza.api.validation;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import io.github.erbayaskin.eimza.api.truststore.DatabaseTrustedCertificateProvider;
import io.github.erbayaskin.eimza.validation.CertificateValidator;
import io.github.erbayaskin.eimza.validation.DefaultCertificateValidator;
import io.github.erbayaskin.eimza.validation.NetworkRevocationDataProvider;
import io.github.erbayaskin.eimza.validation.RevocationDataProvider;
import io.github.erbayaskin.eimza.validation.RevocationEvidence;
import io.github.erbayaskin.eimza.validation.RevocationStatus;

@Configuration
public class ValidationConfiguration {

    @Bean
    @Profile("!local & !test")
    RevocationDataProvider networkRevocationDataProvider(ValidationProperties properties) {
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.getNetworkTimeout())
                .build();
        return new NetworkRevocationDataProvider(
                client,
                properties.getNetworkTimeout(),
                properties.getMaximumRevocationAge(),
                properties.getClockSkew());
    }

    @Bean
    @Profile({"local", "test"})
    RevocationDataProvider localRevocationDataProvider() {
        return (certificate, issuer, validationTime) -> new RevocationEvidence(
                RevocationStatus.UNAVAILABLE,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                new byte[0]);
    }

    @Bean
    CertificateValidator certificateValidator(
            DatabaseTrustedCertificateProvider trustedCertificates,
            RevocationDataProvider revocationDataProvider) {
        return new DefaultCertificateValidator(trustedCertificates, revocationDataProvider);
    }
}
