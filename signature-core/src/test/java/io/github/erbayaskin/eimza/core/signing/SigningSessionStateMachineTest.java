package io.github.erbayaskin.eimza.core.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SigningSessionStateMachineTest {

    @Test
    void acceptsExpectedHappyPath() {
        var status = SigningSessionStatus.CREATED;
        status = SigningSessionStateMachine.transition(status, SigningSessionStatus.MANIFEST_ISSUED);
        status = SigningSessionStateMachine.transition(status, SigningSessionStatus.AGENT_CONNECTED);
        status =
                SigningSessionStateMachine.transition(
                        status, SigningSessionStatus.USER_APPROVAL_PENDING);
        status = SigningSessionStateMachine.transition(status, SigningSessionStatus.CARD_SIGNING);
        status =
                SigningSessionStateMachine.transition(
                        status, SigningSessionStatus.SIGNATURE_RECEIVED);
        status = SigningSessionStateMachine.transition(status, SigningSessionStatus.VALIDATING);
        status = SigningSessionStateMachine.transition(status, SigningSessionStatus.TIMESTAMPING);
        status = SigningSessionStateMachine.transition(status, SigningSessionStatus.COMPLETED);

        assertThat(status).isEqualTo(SigningSessionStatus.COMPLETED);
        assertThat(status.isTerminal()).isTrue();
    }

    @Test
    void rejectsSkippingUserApproval() {
        assertThatThrownBy(
                        () ->
                                SigningSessionStateMachine.transition(
                                        SigningSessionStatus.AGENT_CONNECTED,
                                        SigningSessionStatus.CARD_SIGNING))
                .isInstanceOf(InvalidSigningStateTransitionException.class);
    }

    @Test
    void permitsServerSideSigningWithoutAgentStates() {
        assertThat(SigningSessionStateMachine.transition(
                        SigningSessionStatus.CREATED,
                        SigningSessionStatus.CARD_SIGNING))
                .isEqualTo(SigningSessionStatus.CARD_SIGNING);
    }

    @Test
    void permitsGracePeriodFinalization() {
        assertThat(
                        SigningSessionStateMachine.transition(
                                SigningSessionStatus.TIMESTAMPING,
                                SigningSessionStatus.PROVISIONALLY_COMPLETED))
                .isEqualTo(SigningSessionStatus.PROVISIONALLY_COMPLETED);
        assertThat(
                        SigningSessionStateMachine.transition(
                                SigningSessionStatus.PROVISIONALLY_COMPLETED,
                                SigningSessionStatus.FINALIZING))
                .isEqualTo(SigningSessionStatus.FINALIZING);
    }
}
