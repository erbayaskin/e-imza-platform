package io.github.erbayaskin.eimza.xades;

public class XadesException extends RuntimeException {
    private final String code;

    public XadesException(String code, String message) {
        super(message);
        this.code = code;
    }

    public XadesException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
