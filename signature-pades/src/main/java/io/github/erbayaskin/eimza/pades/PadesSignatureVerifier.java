package io.github.erbayaskin.eimza.pades;

import java.security.cert.TrustAnchor;
import java.util.Set;
import org.apache.pdfbox.Loader;
import io.github.erbayaskin.eimza.cades.CadesSignatureInspector;
import io.github.erbayaskin.eimza.cades.CadesSignatureVerifier;
import io.github.erbayaskin.eimza.cades.SignaturePolicy;

/** Verifies the PDF ByteRange and its ETSI.CAdES.detached CMS container. */
public final class PadesSignatureVerifier {

    public PadesVerificationResult verify(
            byte[] signedPdf, SignaturePolicy expectedPolicy, Set<TrustAnchor> tsaTrustAnchors) {
        return verify(signedPdf, expectedPolicy, tsaTrustAnchors, true);
    }

    public PadesVerificationResult verify(
            byte[] signedPdf,
            SignaturePolicy expectedPolicy,
            Set<TrustAnchor> tsaTrustAnchors,
            boolean certificateValidityCheckActive) {
        try (var document = Loader.loadPDF(signedPdf)) {
            var signatures = document.getSignatureDictionaries();
            if (signatures.isEmpty()) {
                throw new PadesException(
                        "PADES_SIGNATURE_COUNT_INVALID",
                        "PDF en az bir imza içermelidir.");
            }
            io.github.erbayaskin.eimza.cades.CadesVerificationResult verification = null;
            byte[] signerCertificate = null;
            java.util.List<byte[]> embeddedCertificates = java.util.List.of();
            for (var pdfSignature : signatures) {
                var signedContent = pdfSignature.getSignedContent(signedPdf);
                var cms = pdfSignature.getContents(signedPdf);
                verification = new CadesSignatureVerifier().verify(
                        signedContent,
                        cms,
                        expectedPolicy,
                        tsaTrustAnchors,
                        certificateValidityCheckActive);
                var inspection = new CadesSignatureInspector().inspect(cms);
                signerCertificate = inspection.signerCertificate();
                embeddedCertificates = inspection.embeddedCertificates();
            }
            return new PadesVerificationResult(
                    verification,
                    signerCertificate,
                    embeddedCertificates);
        } catch (PadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PadesException("PADES_VALIDATION_FAILED", "PAdES doğrulaması başarısız.", exception);
        }
    }
}
