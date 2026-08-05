package io.github.erbayaskin.eimza.timestamp;

public class TimestampException extends RuntimeException {

    private final String code;

    public TimestampException(String code, String message) {
        super(message);
        this.code = code;
    }

    public TimestampException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
