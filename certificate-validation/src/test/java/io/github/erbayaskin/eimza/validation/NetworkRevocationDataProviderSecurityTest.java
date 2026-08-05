package io.github.erbayaskin.eimza.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NetworkRevocationDataProviderSecurityTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://127.0.0.1/ocsp",
                "http://localhost/ocsp",
                "http://10.0.0.1/ocsp",
                "http://172.16.0.1/crl",
                "http://192.168.1.1/crl",
                "http://[::1]/ocsp",
                "file:///etc/passwd",
                "https://user:password@example.test/ocsp"
            })
    void rejectsPrivateLocalAndMalformedRevocationEndpoints(String endpoint) {
        assertThatThrownBy(() ->
                        NetworkRevocationDataProvider.validatePublicEndpoint(URI.create(endpoint)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
