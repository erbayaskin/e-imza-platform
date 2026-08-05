package io.github.erbayaskin.eimza.api.longterm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class TsaProfileServiceIntegrationTest {
    @Autowired private TsaProfileService service;

    @Test
    void publishesVersionedProfileWithoutPersistingSecretValue() {
        var result = service.update(new UpdateTsaProfileRequest(
                "test-tsa",
                "https://tsa.example.invalid/rfc3161",
                20,
                "TSA_AUTHORIZATION",
                "1.2.3.4.5",
                true));

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.credentialRef()).isEqualTo("TSA_AUTHORIZATION");
        assertThat(result.endpoint()).startsWith("https://");
        assertThat(service.current()).isEqualTo(result);
    }
}
