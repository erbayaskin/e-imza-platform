package io.github.erbayaskin.eimza.core.signing;

public enum SigningSessionStatus {
    CREATED,
    MANIFEST_ISSUED,
    AGENT_CONNECTED,
    USER_APPROVAL_PENDING,
    CARD_SIGNING,
    SIGNATURE_RECEIVED,
    VALIDATING,
    TIMESTAMPING,
    PROVISIONALLY_COMPLETED,
    FINALIZING,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED,
    USER_REJECTED;

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, FAILED, CANCELLED, EXPIRED, USER_REJECTED -> true;
            default -> false;
        };
    }
}
