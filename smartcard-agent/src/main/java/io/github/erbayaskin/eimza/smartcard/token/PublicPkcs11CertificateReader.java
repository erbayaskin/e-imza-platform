package io.github.erbayaskin.eimza.smartcard.token;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.CardProfile;
import io.github.erbayaskin.eimza.smartcard.error.AgentException;

/**
 * Reads public certificate objects directly through Cryptoki. SunPKCS11's
 * KeyStore can attempt a login when the token has CKF_LOGIN_REQUIRED even
 * though public certificate values do not require authentication.
 */
@Component
public class PublicPkcs11CertificateReader {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PublicPkcs11CertificateReader.class);
    private static final long CKR_OK = 0;
    private static final long CKR_CRYPTOKI_ALREADY_INITIALIZED = 0x191;
    private static final long CKF_SERIAL_SESSION = 0x4;
    private static final long CKA_CLASS = 0;
    private static final long CKA_VALUE = 0x11;
    private static final long CKO_CERTIFICATE = 1;
    private static final int MAX_OBJECTS = 256;
    private final AkisCifPublicCertificateReader akisCifReader;

    public PublicPkcs11CertificateReader(
            AkisCifPublicCertificateReader akisCifReader) {
        this.akisCifReader = akisCifReader;
    }

    public List<CardCertificate> read(CardProfile profile) {
        var akisCertificates = akisCifReader.read(profile);
        if (akisCertificates.isPresent()) {
            return akisCertificates.orElseThrow();
        }
        try {
            var cryptoki = Native.load(
                    profile.pkcs11Library().toAbsolutePath().normalize().toString(),
                    Cryptoki.class);
            var initializeResult = unsigned(cryptoki.C_Initialize(null));
            if (initializeResult != CKR_OK
                    && initializeResult != CKR_CRYPTOKI_ALREADY_INITIALIZED) {
                throw failure("C_Initialize", initializeResult);
            }

            var count = new NativeLongByReference();
            requireOk("C_GetSlotList(count)", cryptoki.C_GetSlotList((byte) 1, null, count));
            var slotCount = count.getValue().intValue();
            if (slotCount == 0) {
                throw new AgentException(
                        "CARD_REMOVED", "PKCS#11 token slotunda kart bulunamadı.");
            }
            var slots = new Memory((long) slotCount * NativeLong.SIZE);
            requireOk("C_GetSlotList", cryptoki.C_GetSlotList((byte) 1, slots, count));

            var certificates = new LinkedHashMap<String, CardCertificate>();
            for (int index = 0; index < count.getValue().intValue(); index++) {
                var slotId = slots.getNativeLong((long) index * NativeLong.SIZE);
                readSlot(cryptoki, slotId, certificates);
            }
            LOGGER.info(
                    "Public PKCS#11 sertifikaları PIN kullanılmadan okundu: profileId={}, count={}",
                    profile.id(),
                    certificates.size());
            return certificates.values().stream()
                    .sorted(Comparator.comparing(CardCertificate::fingerprintSha256))
                    .toList();
        } catch (AgentException exception) {
            throw exception;
        } catch (Exception | LinkageError exception) {
            LOGGER.error(
                    "Public PKCS#11 sertifika okuma başarısız: profileId={}",
                    profile.id(),
                    exception);
            throw new AgentException(
                    "PKCS11_PUBLIC_CERTIFICATE_READ_FAILED",
                    "Kartın public sertifikaları PIN kullanılmadan okunamadı.",
                    exception);
        }
    }

    private void readSlot(
            Cryptoki cryptoki,
            NativeLong slotId,
            LinkedHashMap<String, CardCertificate> result)
            throws Exception {
        var session = new NativeLongByReference();
        var openResult = unsigned(cryptoki.C_OpenSession(
                slotId, new NativeLong(CKF_SERIAL_SESSION), null, null, session));
        if (openResult != CKR_OK) {
            LOGGER.debug(
                    "Public sertifika için slot açılamadı: slotId={}, rv=0x{}",
                    slotId,
                    Long.toHexString(openResult));
            return;
        }
        try {
            // AKİS expects an explicit object class while enumerating public
            // certificate objects.
            var certificateTemplate =
                    new AttributeLayout().descriptor(CKA_CLASS, CKO_CERTIFICATE);
            requireOk(
                    "C_FindObjectsInit",
                    cryptoki.C_FindObjectsInit(
                            session.getValue(),
                            certificateTemplate,
                            new NativeLong(1)));
            try {
                var handles = new Memory((long) MAX_OBJECTS * NativeLong.SIZE);
                var found = new NativeLongByReference();
                do {
                    requireOk(
                            "C_FindObjects",
                            cryptoki.C_FindObjects(
                                    session.getValue(),
                                    handles,
                                    new NativeLong(MAX_OBJECTS),
                                    found));
                    for (int index = 0; index < found.getValue().intValue(); index++) {
                        var handle =
                                handles.getNativeLong((long) index * NativeLong.SIZE);
                        var encoded = attribute(
                                cryptoki, session.getValue(), handle, CKA_VALUE);
                        if (encoded == null) {
                            continue;
                        }
                        try {
                            var certificate = (X509Certificate) CertificateFactory
                                    .getInstance("X.509")
                                    .generateCertificate(new ByteArrayInputStream(encoded));
                            var fingerprint = fingerprint(certificate);
                            result.putIfAbsent(
                                    fingerprint,
                                    toCardCertificate(fingerprint, certificate));
                        } catch (Exception ignored) {
                            // Non-certificate objects can also have CKA_VALUE.
                        }
                    }
                } while (found.getValue().intValue() == MAX_OBJECTS);
            } finally {
                cryptoki.C_FindObjectsFinal(session.getValue());
            }
        } finally {
            cryptoki.C_CloseSession(session.getValue());
        }
    }

    private static byte[] attribute(
            Cryptoki cryptoki, NativeLong session, NativeLong object, long type) {
        var layout = new AttributeLayout();
        var descriptor = layout.descriptor(type);
        var firstResult = unsigned(cryptoki.C_GetAttributeValue(
                session, object, descriptor, new NativeLong(1)));
        if (firstResult != CKR_OK) {
            return null;
        }
        var length = descriptor.getNativeLong(layout.lengthOffset).longValue();
        if (length <= 0 || length > 1024 * 1024) {
            return null;
        }
        var value = new Memory(length);
        descriptor.setPointer(layout.valueOffset, value);
        descriptor.setNativeLong(layout.lengthOffset, new NativeLong(length));
        var secondResult = unsigned(cryptoki.C_GetAttributeValue(
                session, object, descriptor, new NativeLong(1)));
        return secondResult == CKR_OK
                ? value.getByteArray(0, (int) length)
                : null;
    }

    private static CardCertificate toCardCertificate(
            String fingerprint, X509Certificate certificate) throws Exception {
        var usage = certificate.getKeyUsage();
        var signingCandidate = usage == null
                || (usage.length > 0 && usage[0])
                || (usage.length > 1 && usage[1]);
        return new CardCertificate(
                fingerprint,
                certificate.getSubjectX500Principal().getName(),
                certificate.getIssuerX500Principal().getName(),
                certificate.getSerialNumber().toString(16).toUpperCase(),
                certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant(),
                certificate.getPublicKey().getAlgorithm(),
                signingCandidate,
                java.util.Base64.getEncoder()
                        .encodeToString(certificate.getEncoded()));
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(certificate.getEncoded()));
    }

    private static void requireOk(String operation, NativeLong result) {
        var value = unsigned(result);
        if (value != CKR_OK) {
            throw failure(operation, value);
        }
    }

    private static AgentException failure(String operation, long result) {
        return new AgentException(
                "PKCS11_PUBLIC_CERTIFICATE_READ_FAILED",
                operation
                        + " başarısız (CK_RV=0x"
                        + Long.toHexString(result).toUpperCase()
                        + ").");
    }

    private static long unsigned(NativeLong value) {
        return NativeLong.SIZE == Integer.BYTES
                ? Integer.toUnsignedLong(value.intValue())
                : value.longValue();
    }

    private static final class AttributeLayout {
        private final long valueOffset = align(NativeLong.SIZE, Native.POINTER_SIZE);
        private final long lengthOffset = valueOffset + Native.POINTER_SIZE;
        private final long size = align(
                lengthOffset + NativeLong.SIZE,
                Math.max(NativeLong.SIZE, Native.POINTER_SIZE));

        Memory descriptor(long type) {
            var memory = new Memory(size);
            memory.clear();
            memory.setNativeLong(0, new NativeLong(type));
            return memory;
        }

        Memory descriptor(long type, long nativeValue) {
            var memory = new Memory(size + NativeLong.SIZE);
            memory.clear();
            var value = memory.share(size);
            value.setNativeLong(0, new NativeLong(nativeValue));
            memory.setNativeLong(0, new NativeLong(type));
            memory.setPointer(valueOffset, value);
            memory.setNativeLong(lengthOffset, new NativeLong(NativeLong.SIZE));
            return memory;
        }

        private static long align(long value, long alignment) {
            return (value + alignment - 1) / alignment * alignment;
        }
    }

    private interface Cryptoki extends Library {
        NativeLong C_Initialize(Pointer initArgs);

        NativeLong C_GetSlotList(
                byte tokenPresent,
                Pointer slotList,
                NativeLongByReference count);

        NativeLong C_OpenSession(
                NativeLong slotId,
                NativeLong flags,
                Pointer application,
                Pointer notify,
                NativeLongByReference session);

        NativeLong C_CloseSession(NativeLong session);

        NativeLong C_FindObjectsInit(
                NativeLong session, Pointer template, NativeLong attributeCount);

        NativeLong C_FindObjects(
                NativeLong session,
                Pointer objectHandles,
                NativeLong maximumObjectCount,
                NativeLongByReference objectCount);

        NativeLong C_FindObjectsFinal(NativeLong session);

        NativeLong C_GetAttributeValue(
                NativeLong session,
                NativeLong object,
                Pointer template,
                NativeLong attributeCount);
    }
}
