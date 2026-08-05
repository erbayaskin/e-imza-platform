package io.github.erbayaskin.eimza.pades;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Calendar;
import java.util.HexFormat;
import java.util.TimeZone;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import io.github.erbayaskin.eimza.cades.CadesSignatureAlgorithm;
import io.github.erbayaskin.eimza.cades.CadesSignatureService;
import io.github.erbayaskin.eimza.cades.SignaturePolicy;
import io.github.erbayaskin.eimza.timestamp.TimestampClient;

/** ETSI EN 319 142 PAdES baseline producer with a detached CAdES CMS container. */
public final class PadesSignatureService {
    private final CadesSignatureService cades = new CadesSignatureService();

    public PadesSigningPreparation prepare(
            byte[] pdf,
            X509Certificate certificate,
            CadesSignatureAlgorithm algorithm,
            SignaturePolicy policy,
            Instant signingTime,
            String signerName,
            String reason) {
        return prepare(
                pdf,
                certificate,
                algorithm,
                policy,
                signingTime,
                signerName,
                reason,
                true);
    }

    public PadesSigningPreparation prepare(
            byte[] pdf,
            X509Certificate certificate,
            CadesSignatureAlgorithm algorithm,
            SignaturePolicy policy,
            Instant signingTime,
            String signerName,
            String reason,
            boolean certificateValidityCheckActive) {
        try (var document = Loader.loadPDF(pdf); var output = new ByteArrayOutputStream()) {
            if (document.getNumberOfPages() < 1) {
                throw new PadesException("PDF_INVALID", "PDF en az bir sayfa icermelidir.");
            }
            var signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            signature.setName(signerName);
            signature.setReason(reason);
            var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(signingTime.toEpochMilli());
            signature.setSignDate(calendar);
            document.addSignature(signature);
            ExternalSigningSupport external = document.saveIncrementalForExternalSigning(output);
            var content = external.getContent().readAllBytes();
            var cmsPreparation = cades.prepare(
                    content,
                    certificate,
                    algorithm,
                    policy,
                    signingTime,
                    false,
                    certificateValidityCheckActive);
            // Finalize ByteRange with an empty CMS value; complete() replaces only the
            // preallocated hex Contents bytes, leaving the signed ranges unchanged.
            external.setSignature(new byte[0]);
            return new PadesSigningPreparation(output.toByteArray(), cmsPreparation);
        } catch (PadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PadesException("PADES_PREPARATION_FAILED", "PAdES hazirligi basarisiz.", exception);
        }
    }

    /**
     * Prepares a new incremental PDF revision over an already signed PDF.
     * Each invocation appends one independent approval signature.
     */
    public PadesSigningPreparation prepareSequential(
            byte[] signedPdf,
            X509Certificate certificate,
            CadesSignatureAlgorithm algorithm,
            SignaturePolicy policy,
            Instant signingTime,
            String signerName,
            String reason,
            boolean certificateValidityCheckActive) {
        if (signedPdf == null || signedPdf.length == 0) {
            throw new PadesException(
                    "EXISTING_SIGNATURE_REQUIRED",
                    "Seri PAdES için önceki imzalı PDF zorunludur.");
        }
        return prepare(
                signedPdf,
                certificate,
                algorithm,
                policy,
                signingTime,
                signerName,
                reason,
                certificateValidityCheckActive);
    }

    public PadesSignatureResult completeBaseline(
            PadesSigningPreparation preparation, byte[] rawSignature) {
        var cms = cades.completeBaseline(preparation.cmsPreparation(), rawSignature);
        return new PadesSignatureResult(inject(preparation.preparedPdf(), cms.encodedSignature()),
                "B-B", null, null);
    }

    public PadesSignatureResult completeWithTimestamp(
            PadesSigningPreparation preparation,
            byte[] rawSignature,
            TimestampClient timestampClient,
            String timestampPolicyOid) {
        var cms = cades.completeWithTimestamp(
                preparation.cmsPreparation(), rawSignature, timestampClient, timestampPolicyOid);
        return new PadesSignatureResult(
                inject(preparation.preparedPdf(), cms.encodedSignature()),
                "B-T",
                cms.timestampGenerationTime(),
                cms.timestampPolicyOid());
    }

    static byte[] inject(byte[] preparedPdf, byte[] cms) {
        var marker = "/Contents".getBytes(StandardCharsets.US_ASCII);
        var start = lastIndexOf(preparedPdf, marker);
        if (start < 0) {
            throw new PadesException("PDF_SIGNATURE_PLACEHOLDER_MISSING", "PDF imza alani bulunamadi.");
        }
        var hexStart = start + marker.length;
        while (hexStart < preparedPdf.length
                && (preparedPdf[hexStart] == ' ' || preparedPdf[hexStart] == '\r'
                || preparedPdf[hexStart] == '\n' || preparedPdf[hexStart] == '\t')) {
            hexStart++;
        }
        if (hexStart >= preparedPdf.length || preparedPdf[hexStart] != '<') {
            throw new PadesException("PDF_SIGNATURE_PLACEHOLDER_MISSING", "PDF imza alani bulunamadi.");
        }
        hexStart++;
        var hexEnd = hexStart;
        while (hexEnd < preparedPdf.length && preparedPdf[hexEnd] != '>') {
            hexEnd++;
        }
        var capacity = hexEnd - hexStart;
        var encoded = HexFormat.of().withUpperCase().formatHex(cms)
                .getBytes(StandardCharsets.US_ASCII);
        if (encoded.length > capacity) {
            throw new PadesException("PDF_SIGNATURE_TOO_LARGE", "CMS imzasi ayrilan PDF alanina sigmadi.");
        }
        var result = preparedPdf.clone();
        System.arraycopy(encoded, 0, result, hexStart, encoded.length);
        java.util.Arrays.fill(result, hexStart + encoded.length, hexEnd, (byte) '0');
        return result;
    }

    private static int lastIndexOf(byte[] source, byte[] target) {
        outer:
        for (int i = source.length - target.length; i >= 0; i--) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
