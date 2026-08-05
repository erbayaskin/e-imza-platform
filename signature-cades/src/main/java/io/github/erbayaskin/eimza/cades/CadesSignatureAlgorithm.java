package io.github.erbayaskin.eimza.cades;

import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

public enum CadesSignatureAlgorithm {
    RSA_PKCS1_SHA256(
            "RSA",
            "SHA-256",
            "SHA256withRSA",
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
            new AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE)),
    RSA_PKCS1_SHA384(
            "RSA",
            "SHA-384",
            "SHA384withRSA",
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384),
            new AlgorithmIdentifier(PKCSObjectIdentifiers.sha384WithRSAEncryption, DERNull.INSTANCE)),
    RSA_PKCS1_SHA512(
            "RSA",
            "SHA-512",
            "SHA512withRSA",
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha512),
            new AlgorithmIdentifier(PKCSObjectIdentifiers.sha512WithRSAEncryption, DERNull.INSTANCE)),
    ECDSA_SHA256(
            "EC",
            "SHA-256",
            "SHA256withECDSA",
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
            new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256)),
    ECDSA_SHA384(
            "EC",
            "SHA-384",
            "SHA384withECDSA",
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384),
            new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA384)),
    ECDSA_SHA512(
            "EC",
            "SHA-512",
            "SHA512withECDSA",
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha512),
            new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA512));

    public static final AlgorithmIdentifier SHA256 =
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256);

    private final String keyAlgorithm;
    private final String digestName;
    private final String jcaName;
    private final AlgorithmIdentifier digestIdentifier;
    private final AlgorithmIdentifier cmsIdentifier;

    CadesSignatureAlgorithm(
            String keyAlgorithm,
            String digestName,
            String jcaName,
            AlgorithmIdentifier digestIdentifier,
            AlgorithmIdentifier cmsIdentifier) {
        this.keyAlgorithm = keyAlgorithm;
        this.digestName = digestName;
        this.jcaName = jcaName;
        this.digestIdentifier = digestIdentifier;
        this.cmsIdentifier = cmsIdentifier;
    }

    public String keyAlgorithm() {
        return keyAlgorithm;
    }

    public String jcaName() {
        return jcaName;
    }

    public String digestName() {
        return digestName;
    }

    public AlgorithmIdentifier digestIdentifier() {
        return digestIdentifier;
    }

    public AlgorithmIdentifier cmsIdentifier() {
        return cmsIdentifier;
    }
}
