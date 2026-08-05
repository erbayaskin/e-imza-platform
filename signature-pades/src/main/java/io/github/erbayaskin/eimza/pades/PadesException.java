package io.github.erbayaskin.eimza.pades;

public class PadesException extends RuntimeException {
    private final String code;
    public PadesException(String code, String message) {
        super(message);
        this.code = code;
    }
    public PadesException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    public String code() { return code; }
}
