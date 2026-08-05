package io.github.erbayaskin.eimza.cades;

import java.time.Instant;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;

public record CadesSigningPreparation(
        byte[] signedAttributes,
        byte[] digestToSign,
        byte[] signerCertificate,
        CadesSignatureAlgorithm signatureAlgorithm,
        SignaturePolicy signaturePolicy,
        Instant signingTime,
        byte[] encapsulatedContent,
        byte[] existingSignature,
        MultiSignatureType multiSignatureType,
        int targetSignatureIndex) {

    public CadesSigningPreparation(
            byte[] signedAttributes,
            byte[] digestToSign,
            byte[] signerCertificate,
            CadesSignatureAlgorithm signatureAlgorithm,
            SignaturePolicy signaturePolicy,
            Instant signingTime,
            byte[] encapsulatedContent) {
        this(
                signedAttributes,
                digestToSign,
                signerCertificate,
                signatureAlgorithm,
                signaturePolicy,
                signingTime,
                encapsulatedContent,
                null,
                MultiSignatureType.SINGLE,
                0);
    }

    public CadesSigningPreparation {
        signedAttributes = signedAttributes.clone();
        digestToSign = digestToSign.clone();
        signerCertificate = signerCertificate.clone();
        encapsulatedContent = encapsulatedContent == null ? null : encapsulatedContent.clone();
        existingSignature = existingSignature == null ? null : existingSignature.clone();
        multiSignatureType =
                multiSignatureType == null ? MultiSignatureType.SINGLE : multiSignatureType;
        if (targetSignatureIndex < 0) {
            throw new IllegalArgumentException("targetSignatureIndex negatif olamaz.");
        }
    }

    @Override public byte[] signedAttributes() { return signedAttributes.clone(); }
    @Override public byte[] digestToSign() { return digestToSign.clone(); }
    @Override public byte[] signerCertificate() { return signerCertificate.clone(); }
    @Override public byte[] encapsulatedContent() {
        return encapsulatedContent == null ? null : encapsulatedContent.clone();
    }
    @Override public byte[] existingSignature() {
        return existingSignature == null ? null : existingSignature.clone();
    }
}
