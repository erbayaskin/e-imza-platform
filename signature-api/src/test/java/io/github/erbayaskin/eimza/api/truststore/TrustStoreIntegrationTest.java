package io.github.erbayaskin.eimza.api.truststore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.validation.TrustedCertificateProvider;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class TrustStoreIntegrationTest {

    @Autowired
    private TrustStoreService service;

    @Autowired
    private TrustedCertificateProvider provider;

    @Test
    void addUpdateRemovePublishesImmutableHistoricalSnapshots() throws IOException {
        var certificatePem =
                new ClassPathResource("certificates/test-root-ca.pem")
                        .getContentAsString(StandardCharsets.US_ASCII);

        var added =
                service.add(
                        new CreateTrustedCertificateRequest(
                                certificatePem,
                                TrustedCertificateType.ROOT,
                                "Test kök sertifikası",
                                true));

        assertThat(added.version()).isNotBlank();
        assertThat(added.certificates()).hasSize(1);
        var certificateId = added.certificates().getFirst().certificateId();

        var updated =
                service.update(
                        certificateId,
                        new UpdateTrustedCertificateRequest(
                                TrustedCertificateType.ROOT,
                                "Güncellenmiş test kökü",
                                true));

        assertThat(updated.version()).isNotEqualTo(added.version());
        assertThat(updated.certificates().getFirst().displayName())
                .isEqualTo("Güncellenmiş test kökü");

        var historical = provider.snapshotAt(added.validFrom());
        assertThat(historical.version()).isEqualTo(added.version());
        assertThat(historical.certificates()).hasSize(1);

        var removed = service.remove(certificateId);
        assertThat(removed.version()).isNotEqualTo(updated.version());
        assertThat(removed.certificates()).isEmpty();
    }
}
