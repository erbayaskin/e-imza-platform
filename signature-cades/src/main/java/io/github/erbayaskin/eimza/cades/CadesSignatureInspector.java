package io.github.erbayaskin.eimza.cades;

import java.util.ArrayList;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.OCSPResponse;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cms.CMSSignedData;

public class CadesSignatureInspector {

    public CadesInspection inspect(byte[] encodedSignature) {
        try {
            var signedData = new CMSSignedData(encodedSignature);
            var signers = signedData.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                throw new CadesException(
                        "SIGNER_COUNT_INVALID",
                        "CAdES en az bir imzalayan içermelidir.");
            }
            var signer = signers.iterator().next();
            var matches = signedData.getCertificates().getMatches(signer.getSID());
            if (matches.size() != 1) {
                throw new CadesException(
                        "SIGNER_CERTIFICATE_INVALID",
                        "İmzalayan sertifikası tekil olarak bulunamadı.");
            }
            var signerHolder = (X509CertificateHolder) matches.iterator().next();
            var embedded = new ArrayList<byte[]>();
            for (X509CertificateHolder holder : signedData.getCertificates().getMatches(null)) {
                if (!holder.equals(signerHolder)) {
                    embedded.add(holder.getEncoded());
                }
            }
            var revocations = new ArrayList<CadesRevocationValue>();
            for (X509CRLHolder holder : signedData.getCRLs().getMatches(null)) {
                revocations.add(new CadesRevocationValue(
                        CadesRevocationType.CRL, holder.getEncoded()));
            }
            for (Object value : signedData
                    .getOtherRevocationInfo(CMSObjectIdentifiers.id_ri_ocsp_response)
                    .getMatches(null)) {
                revocations.add(new CadesRevocationValue(
                        CadesRevocationType.OCSP,
                        OCSPResponse.getInstance(value).getEncoded("DER")));
            }
            return new CadesInspection(
                    signerHolder.getEncoded(), embedded, revocations);
        } catch (CadesException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CadesException("CADES_INVALID", "CAdES sertifikaları ayrıştırılamadı.", exception);
        }
    }
}
