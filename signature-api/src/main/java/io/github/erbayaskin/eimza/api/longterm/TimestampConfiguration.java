package io.github.erbayaskin.eimza.api.longterm;

import java.net.URI;
import java.net.http.HttpClient;
import java.security.cert.TrustAnchor;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.erbayaskin.eimza.api.truststore.DatabaseTrustedCertificateProvider;
import io.github.erbayaskin.eimza.timestamp.Rfc3161TimestampClient;
import io.github.erbayaskin.eimza.timestamp.TimestampClient;
import io.github.erbayaskin.eimza.validation.TrustedCertificateProvider;

@Configuration
public class TimestampConfiguration {

    @Bean
    TimestampClient configuredTimestampClient(
            TsaProfileService profiles,
            io.github.erbayaskin.eimza.api.serversigning.ServerCredentialProvider credentials,
            DatabaseTrustedCertificateProvider trustedCertificates,
            Clock clock) {
        return request -> {
            var profile = profiles.current();
            if (!profile.enabled() || profile.endpoint().isBlank()) {
                throw new io.github.erbayaskin.eimza.timestamp.TimestampException(
                        "TSA_NOT_CONFIGURED", "Etkin TSA profili bulunamadı.");
            }
            var timeout = java.time.Duration.ofSeconds(profile.requestTimeoutSeconds());
            var client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(timeout)
                    .build();
            Map<String, String> headers = Map.of();
            char[] secret = null;
            try {
                if (profile.credentialRef() != null && !profile.credentialRef().isBlank()) {
                    secret = credentials.resolve(profile.credentialRef());
                    headers = Map.of("Authorization", new String(secret));
                }
            var snapshot = trustedCertificates.snapshotAt(clock.instant());
            var roots = snapshot.certificates().stream()
                    .filter(item -> item.type()
                            == TrustedCertificateProvider.TrustedCertificateType.ROOT)
                    .map(item -> new TrustAnchor(item.certificate(), null))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new Rfc3161TimestampClient(
                            URI.create(profile.endpoint()),
                            profile.providerId(),
                            client,
                            timeout,
                            headers,
                            roots)
                    .timestamp(request);
            } finally {
                if (secret != null) java.util.Arrays.fill(secret, '\0');
            }
        };
    }
}
