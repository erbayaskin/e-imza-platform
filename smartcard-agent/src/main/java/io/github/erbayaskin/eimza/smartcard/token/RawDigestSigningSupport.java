package io.github.erbayaskin.eimza.smartcard.token;

import java.util.HexFormat;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

public final class RawDigestSigningSupport {

    private static final byte[] SHA256_DIGEST_INFO_PREFIX =
            HexFormat.of().parseHex("3031300D060960864801650304020105000420");
    private static final byte[] SHA384_DIGEST_INFO_PREFIX =
            HexFormat.of().parseHex("3041300D060960864801650304020205000430");
    private static final byte[] SHA512_DIGEST_INFO_PREFIX =
            HexFormat.of().parseHex("3051300D060960864801650304020305000440");

    private RawDigestSigningSupport() {}

    public static String jcaAlgorithm(String mechanism) {
        return switch (mechanism) {
            case "RSA_PKCS1_SHA256", "RSA_PKCS1_SHA384", "RSA_PKCS1_SHA512" -> "NONEwithRSA";
            case "ECDSA_SHA256", "ECDSA_SHA384", "ECDSA_SHA512" -> "NONEwithECDSA";
            default -> throw new AgentException("MECHANISM_NOT_ALLOWED", "İmza mekanizması desteklenmiyor.");
        };
    }

    public static byte[] payload(String mechanism, byte[] digest) {
        var expectedLength = switch (mechanism) {
            case "RSA_PKCS1_SHA256", "ECDSA_SHA256" -> 32;
            case "RSA_PKCS1_SHA384", "ECDSA_SHA384" -> 48;
            case "RSA_PKCS1_SHA512", "ECDSA_SHA512" -> 64;
            default -> throw new AgentException(
                    "MECHANISM_NOT_ALLOWED", "İmza mekanizması desteklenmiyor.");
        };
        if (digest.length != expectedLength) {
            throw new AgentException(
                    "INVALID_DIGEST",
                    "Seçilen imza mekanizması için özet " + expectedLength + " bayt olmalıdır.");
        }
        if (mechanism.startsWith("ECDSA_")) {
            return digest.clone();
        }
        var prefix = switch (mechanism) {
            case "RSA_PKCS1_SHA256" -> SHA256_DIGEST_INFO_PREFIX;
            case "RSA_PKCS1_SHA384" -> SHA384_DIGEST_INFO_PREFIX;
            case "RSA_PKCS1_SHA512" -> SHA512_DIGEST_INFO_PREFIX;
            default -> throw new AgentException(
                    "MECHANISM_NOT_ALLOWED", "İmza mekanizması desteklenmiyor.");
        };
        var result = new byte[prefix.length + digest.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(digest, 0, result, prefix.length, digest.length);
        return result;
    }
}
