package io.github.erbayaskin.eimza.smartcard.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RawDigestSigningSupportTest {

    @ParameterizedTest
    @CsvSource({
        "RSA,2048,RSA_PKCS1_SHA256,SHA-256,SHA256withRSA",
        "RSA,2048,RSA_PKCS1_SHA384,SHA-384,SHA384withRSA",
        "RSA,2048,RSA_PKCS1_SHA512,SHA-512,SHA512withRSA",
        "EC,256,ECDSA_SHA256,SHA-256,SHA256withECDSA",
        "EC,384,ECDSA_SHA384,SHA-384,SHA384withECDSA",
        "EC,521,ECDSA_SHA512,SHA-512,SHA512withECDSA"
    })
    void rawDigestSignatureIsCompatibleWithStandardVerification(
            String keyAlgorithm,
            int keySize,
            String mechanism,
            String digestAlgorithm,
            String verificationAlgorithm) throws Exception {
        var keyGenerator = KeyPairGenerator.getInstance(keyAlgorithm);
        keyGenerator.initialize(keySize);
        var keyPair = keyGenerator.generateKeyPair();
        var content = "Faz 4 ham özet imzalama kanıtı".getBytes(StandardCharsets.UTF_8);
        var digest = MessageDigest.getInstance(digestAlgorithm).digest(content);

        var rawSigner = Signature.getInstance(RawDigestSigningSupport.jcaAlgorithm(mechanism));
        rawSigner.initSign(keyPair.getPrivate());
        rawSigner.update(RawDigestSigningSupport.payload(mechanism, digest));
        var signed = rawSigner.sign();

        var verifier = Signature.getInstance(verificationAlgorithm);
        verifier.initVerify(keyPair.getPublic());
        verifier.update(content);
        assertThat(verifier.verify(signed)).isTrue();
    }
}
