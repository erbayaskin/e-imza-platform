package io.github.erbayaskin.eimza.cades;

public class CadesException extends RuntimeException {

    private final String code;

    public CadesException(String code, String message) {
        super(message);
        this.code = code;
    }

    public CadesException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
