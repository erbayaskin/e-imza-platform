package io.github.erbayaskin.eimza.api.longterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import io.github.erbayaskin.eimza.api.error.ApiException;

@SpringBootTest
@ActiveProfiles("local")
class SignatureAugmentationIntegrationTest {

    @Autowired
    private SignatureAugmentationRepository repository;

    @Autowired
    private TimestampProperties timestampProperties;

    @Autowired
    private java.time.Clock clock;

    @Test
    void migrationPersistsAugmentationAuditAndLtaRequiresConfiguredTsa() {
        var id = UUID.randomUUID();
        repository.save(SignatureAugmentationEntity.create(
                id,
                "a".repeat(64),
                "b".repeat(64),
                "B-LT",
                3,
                2,
                0,
                null,
                Instant.parse("2026-07-28T12:00:00Z")));

        assertThat(repository.findById(id)).isPresent();

        var service = new LongTermSignatureService(
                Optional.empty(), timestampProperties, repository, clock);
        var request = new LongTermAugmentationRequest(
                Base64.getEncoder().encodeToString(new byte[] {1}),
                Base64.getEncoder().encodeToString(new byte[] {2}),
                LongTermTarget.B_LTA,
                List.of(),
                List.of(),
                null);

        assertThatThrownBy(() -> service.augment(request))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("TSA_NOT_CONFIGURED"));
    }
}
