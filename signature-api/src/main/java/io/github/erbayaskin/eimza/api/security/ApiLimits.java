package io.github.erbayaskin.eimza.api.security;

public final class ApiLimits {

    public static final int MAX_DOCUMENT_BYTES = 25 * 1024 * 1024;
    public static final int MAX_SIGNATURE_BYTES = 25 * 1024 * 1024;
    public static final int MAX_CERTIFICATE_BYTES = 1024 * 1024;
    public static final int MAX_REVOCATION_VALUE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_BASE64_DOCUMENT_CHARS = 34_952_536;
    public static final int MAX_BASE64_CERTIFICATE_CHARS = 1_398_104;
    public static final int MAX_BASE64_REVOCATION_CHARS = 6_990_508;

    private ApiLimits() {}
}
