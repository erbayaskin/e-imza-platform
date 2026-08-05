package io.github.erbayaskin.eimza.api.validation.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import io.github.erbayaskin.eimza.api.error.ApiException;

class ValidationPolicyConfigurationServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void allowsAuditOnlyInLocalProfileAndForcesAllOptionalPoliciesPassive() {
        var repository = mock(ValidationPolicyConfigurationRepository.class);
        when(repository.findFirstByOrderByVersionNumberDesc()).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        var service = new ValidationPolicyConfigurationService(repository, environment, CLOCK);

        var result = service.update(new UpdateValidationPolicyRequest(
                ValidationPolicyMode.AUDIT_ONLY, true, true, true, true, true));

        assertThat(result.mode()).isEqualTo(ValidationPolicyMode.AUDIT_ONLY);
        assertThat(result.auditOnlyAllowed()).isTrue();
        assertThat(result.qcComplianceActive()).isFalse();
        assertThat(result.certificatePolicyActive()).isFalse();
        assertThat(result.revocationActive()).isFalse();
        assertThat(result.signaturePolicyActive()).isFalse();
        assertThat(result.signingCertificateValidityActive()).isFalse();
    }

    @Test
    void rejectsAuditOnlyOutsideLocalAndTestProfiles() {
        var repository = mock(ValidationPolicyConfigurationRepository.class);
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var service = new ValidationPolicyConfigurationService(repository, environment, CLOCK);

        assertThatThrownBy(() -> service.update(new UpdateValidationPolicyRequest(
                        ValidationPolicyMode.AUDIT_ONLY, false, false, false, false, false)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("local ve test");
    }

    @Test
    void strictModeForcesAllOptionalPoliciesActive() {
        var repository = mock(ValidationPolicyConfigurationRepository.class);
        when(repository.findFirstByOrderByVersionNumberDesc()).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var service = new ValidationPolicyConfigurationService(repository, environment, CLOCK);

        var result = service.update(new UpdateValidationPolicyRequest(
                ValidationPolicyMode.STRICT, false, false, false, false, false));

        assertThat(result.qcComplianceActive()).isTrue();
        assertThat(result.certificatePolicyActive()).isTrue();
        assertThat(result.revocationActive()).isTrue();
        assertThat(result.signaturePolicyActive()).isTrue();
        assertThat(result.signingCertificateValidityActive()).isTrue();
    }

    @Test
    void productionRuntimeOverridesPreviouslyStoredNonStrictConfiguration() {
        var repository = mock(ValidationPolicyConfigurationRepository.class);
        var stored = ValidationPolicyConfigurationEntity.create(
                7,
                ValidationPolicyMode.AUDIT_ONLY,
                false,
                false,
                false,
                false,
                false,
                CLOCK.instant());
        when(repository.findFirstByOrderByVersionNumberDesc()).thenReturn(Optional.of(stored));
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var service = new ValidationPolicyConfigurationService(repository, environment, CLOCK);

        var result = service.current();

        assertThat(result.mode()).isEqualTo(ValidationPolicyMode.STRICT);
        assertThat(result.policyCustomizationAllowed()).isFalse();
        assertThat(result.qcComplianceActive()).isTrue();
        assertThat(result.certificatePolicyActive()).isTrue();
        assertThat(result.revocationActive()).isTrue();
        assertThat(result.signaturePolicyActive()).isTrue();
        assertThat(result.signingCertificateValidityActive()).isTrue();
    }
}
