package io.github.erbayaskin.eimza.smartcard.token;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.TerminalFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.smartcard.Atr;
import io.github.erbayaskin.eimza.smartcard.CardProfile;

/**
 * Optional adapter for TÜBİTAK AKİS CIF. The vendor JAR is loaded from the
 * local middleware installation and is not distributed with the agent.
 */
@Component
public class AkisCifPublicCertificateReader {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AkisCifPublicCertificateReader.class);
    private static final long FILE_NOT_FOUND = 0x6A82;

    public Optional<List<CardCertificate>> read(CardProfile profile) {
        var cifLibrary = locateLibrary();
        if (cifLibrary.isEmpty()) {
            return Optional.empty();
        }
        try {
            var result = readWithLibrary(profile, cifLibrary.orElseThrow());
            if (!result.isEmpty()) {
                LOGGER.info(
                        "AKİS CIF public sertifikaları PIN kullanılmadan okundu: profileId={}, count={}",
                        profile.id(),
                        result.size());
                return Optional.of(result);
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.warn(
                    "AKİS CIF public sertifika okuması kullanılamadı; genel PKCS#11 yolu denenecek: profileId={}",
                    profile.id(),
                    exception);
        }
        return Optional.empty();
    }

    private List<CardCertificate> readWithLibrary(
            CardProfile profile, Path library) throws Exception {
        try (var loader = new URLClassLoader(
                new java.net.URL[] {library.toUri().toURL()},
                getClass().getClassLoader())) {
            var transmitterClass =
                    loader.loadClass("tubitak.akis.cif.functions.CommandTransmitterPCSC");
            var transmitterInterface =
                    loader.loadClass("tubitak.akis.cif.functions.ICommandTransmitter");
            var factoryClass = loader.loadClass("tubitak.akis.cif.commands.CIFFactory");
            var factory = factoryClass.getMethod(
                    "getAkisCIFInstance", transmitterInterface);

            for (CardTerminal terminal : TerminalFactory.getDefault().terminals().list()) {
                if (!terminal.isCardPresent()) {
                    continue;
                }
                Object transmitter = null;
                try {
                    transmitter = transmitterClass
                            .getConstructor(CardTerminal.class, boolean.class)
                            .newInstance(terminal, false);
                    var cardAtr = (javax.smartcardio.ATR) transmitterClass
                            .getMethod("atr")
                            .invoke(transmitter);
                    if (!profile.atrPattern().matches(new Atr(cardAtr.getBytes()))) {
                        continue;
                    }
                    var commands = factory.invoke(null, transmitter);
                    commands.getClass().getMethod("selectMF").invoke(commands);
                    commands.getClass()
                            .getMethod("selectDFByName", byte[].class)
                            .invoke(commands, (Object) "PKCS-15".getBytes(StandardCharsets.US_ASCII));
                    return readCertificateFiles(commands);
                } finally {
                    if (transmitter != null) {
                        transmitterClass.getMethod("closeCardTerminal").invoke(transmitter);
                    }
                }
            }
            return List.of();
        }
    }

    private List<CardCertificate> readCertificateFiles(Object commands)
            throws Exception {
        var result = new ArrayList<CardCertificate>();
        var read = commands.getClass().getMethod(
                "readFileBySelectingUnderActiveDF", byte[].class);
        var missingCount = 0;
        for (int fileId = 0x2F10; fileId <= 0x2FFF && missingCount <= 2; fileId++) {
            var id = new byte[] {(byte) (fileId >>> 8), (byte) fileId};
            try {
                var encoded = (byte[]) read.invoke(commands, (Object) id);
                if (encoded == null || encoded.length == 0) {
                    missingCount++;
                    continue;
                }
                missingCount = 0;
                var certificate = (X509Certificate) CertificateFactory
                        .getInstance("X.509")
                        .generateCertificate(new java.io.ByteArrayInputStream(encoded));
                result.add(toCardCertificate(certificate));
            } catch (InvocationTargetException exception) {
                if (errorCode(exception.getCause()) == FILE_NOT_FOUND) {
                    missingCount++;
                    continue;
                }
                throw exception;
            }
        }
        return result.stream()
                .distinct()
                .sorted(Comparator.comparing(CardCertificate::fingerprintSha256))
                .toList();
    }

    private static long errorCode(Throwable exception) {
        try {
            var value = exception.getClass().getMethod("getErrorCode").invoke(exception);
            return ((Number) value).longValue();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static CardCertificate toCardCertificate(X509Certificate certificate)
            throws Exception {
        var encoded = certificate.getEncoded();
        var fingerprint = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(encoded));
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
                Base64.getEncoder().encodeToString(encoded));
    }

    private static Optional<Path> locateLibrary() {
        var configured = System.getenv("EIMZA_AGENT_AKIS_CIF_LIBRARY");
        if (configured != null && !configured.isBlank()) {
            var path = Path.of(configured).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        var programFiles = System.getenv().getOrDefault(
                "ProgramFiles", "C:\\Program Files");
        var directories = List.of(
                Path.of(programFiles, "ImzagerKurumsal", "lib"),
                Path.of(programFiles, "AKIS", "lib"));
        for (var directory : directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (var candidates = Files.list(directory)) {
                var found = candidates
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches(
                                "(?i)akiscif-[0-9.]+\\.jar"))
                        .max(Comparator.comparing(path -> path.getFileName().toString()));
                if (found.isPresent()) {
                    return found;
                }
            } catch (Exception exception) {
                LOGGER.debug("AKİS CIF dizini okunamadı: {}", directory, exception);
            }
        }
        return Optional.empty();
    }
}
