package io.github.erbayaskin.eimza.core.signing;

public final class InvalidSigningStateTransitionException extends RuntimeException {

    public InvalidSigningStateTransitionException(
            SigningSessionStatus current,
            SigningSessionStatus target) {
        super("İmzalama oturumu " + current + " durumundan " + target + " durumuna geçirilemez.");
    }
}
