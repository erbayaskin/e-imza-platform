package io.github.erbayaskin.eimza.xades;

import java.time.Instant;
import io.github.erbayaskin.eimza.core.model.SignaturePackaging;
import io.github.erbayaskin.eimza.core.model.MultiSignatureType;

public record XadesSigningPreparation(
        byte[] signedInfo,
        byte[] digestToSign,
        byte[] signerCertificate,
        String signatureAlgorithm,
        String signatureId,
        String signedProperties,
        String documentUri,
        String documentDigestBase64,
        Instant signingTime,
        SignaturePackaging packaging,
        byte[] documentContent,
        String documentObject,
        String mediaType,
        byte[] existingSignature,
        MultiSignatureType multiSignatureType,
        int targetSignatureIndex) {

    public XadesSigningPreparation(
            byte[] signedInfo,
            byte[] digestToSign,
            byte[] signerCertificate,
            String signatureAlgorithm,
            String signatureId,
            String signedProperties,
            String documentUri,
            String documentDigestBase64,
            Instant signingTime,
            SignaturePackaging packaging,
            byte[] documentContent,
            String documentObject,
            String mediaType) {
        this(
                signedInfo,
                digestToSign,
                signerCertificate,
                signatureAlgorithm,
                signatureId,
                signedProperties,
                documentUri,
                documentDigestBase64,
                signingTime,
                packaging,
                documentContent,
                documentObject,
                mediaType,
                null,
                MultiSignatureType.SINGLE,
                0);
    }

    public XadesSigningPreparation {
        signedInfo = signedInfo.clone();
        digestToSign = digestToSign.clone();
        signerCertificate = signerCertificate.clone();
        documentContent = documentContent == null ? null : documentContent.clone();
        existingSignature = existingSignature == null ? null : existingSignature.clone();
        multiSignatureType =
                multiSignatureType == null ? MultiSignatureType.SINGLE : multiSignatureType;
        if (targetSignatureIndex < 0) {
            throw new IllegalArgumentException("targetSignatureIndex negatif olamaz.");
        }
    }

    @Override public byte[] signedInfo() { return signedInfo.clone(); }
    @Override public byte[] digestToSign() { return digestToSign.clone(); }
    @Override public byte[] signerCertificate() { return signerCertificate.clone(); }
    @Override public byte[] documentContent() {
        return documentContent == null ? null : documentContent.clone();
    }
    @Override public byte[] existingSignature() {
        return existingSignature == null ? null : existingSignature.clone();
    }
}
