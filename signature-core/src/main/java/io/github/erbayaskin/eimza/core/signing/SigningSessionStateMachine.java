package io.github.erbayaskin.eimza.core.signing;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class SigningSessionStateMachine {

    private static final Map<SigningSessionStatus, Set<SigningSessionStatus>> TRANSITIONS =
            createTransitions();

    private SigningSessionStateMachine() {
    }

    public static boolean canTransition(
            SigningSessionStatus current,
            SigningSessionStatus target) {
        return TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    public static SigningSessionStatus transition(
            SigningSessionStatus current,
            SigningSessionStatus target) {
        if (!canTransition(current, target)) {
            throw new InvalidSigningStateTransitionException(current, target);
        }
        return target;
    }

    private static Map<SigningSessionStatus, Set<SigningSessionStatus>> createTransitions() {
        var transitions =
                new EnumMap<SigningSessionStatus, Set<SigningSessionStatus>>(
                        SigningSessionStatus.class);
        transitions.put(
                SigningSessionStatus.CREATED,
                EnumSet.of(
                        SigningSessionStatus.MANIFEST_ISSUED,
                        SigningSessionStatus.CARD_SIGNING,
                        SigningSessionStatus.CANCELLED,
                        SigningSessionStatus.EXPIRED));
        transitions.put(
                SigningSessionStatus.MANIFEST_ISSUED,
                EnumSet.of(
                        SigningSessionStatus.AGENT_CONNECTED,
                        SigningSessionStatus.CANCELLED,
                        SigningSessionStatus.EXPIRED));
        transitions.put(
                SigningSessionStatus.AGENT_CONNECTED,
                EnumSet.of(
                        SigningSessionStatus.USER_APPROVAL_PENDING,
                        SigningSessionStatus.CANCELLED,
                        SigningSessionStatus.EXPIRED));
        transitions.put(
                SigningSessionStatus.USER_APPROVAL_PENDING,
                EnumSet.of(
                        SigningSessionStatus.CARD_SIGNING,
                        SigningSessionStatus.USER_REJECTED,
                        SigningSessionStatus.CANCELLED,
                        SigningSessionStatus.EXPIRED));
        transitions.put(
                SigningSessionStatus.CARD_SIGNING,
                EnumSet.of(
                        SigningSessionStatus.SIGNATURE_RECEIVED,
                        SigningSessionStatus.FAILED,
                        SigningSessionStatus.CANCELLED));
        transitions.put(
                SigningSessionStatus.SIGNATURE_RECEIVED,
                EnumSet.of(SigningSessionStatus.VALIDATING, SigningSessionStatus.FAILED));
        transitions.put(
                SigningSessionStatus.VALIDATING,
                EnumSet.of(SigningSessionStatus.TIMESTAMPING, SigningSessionStatus.FAILED));
        transitions.put(
                SigningSessionStatus.TIMESTAMPING,
                EnumSet.of(
                        SigningSessionStatus.PROVISIONALLY_COMPLETED,
                        SigningSessionStatus.COMPLETED,
                        SigningSessionStatus.FAILED));
        transitions.put(
                SigningSessionStatus.PROVISIONALLY_COMPLETED,
                EnumSet.of(SigningSessionStatus.FINALIZING, SigningSessionStatus.FAILED));
        transitions.put(
                SigningSessionStatus.FINALIZING,
                EnumSet.of(SigningSessionStatus.COMPLETED, SigningSessionStatus.FAILED));
        return Map.copyOf(transitions);
    }
}
