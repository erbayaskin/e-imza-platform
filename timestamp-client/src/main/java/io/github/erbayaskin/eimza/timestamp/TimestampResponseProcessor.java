package io.github.erbayaskin.eimza.timestamp;

import java.security.cert.TrustAnchor;
import java.util.Set;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.tsp.TimeStampRequestGenerator;

public final class TimestampResponseProcessor {

    private final TimestampTokenVerifier tokenVerifier = new TimestampTokenVerifier();

    public TimestampResponseProcessor() {}

    public TimestampResponse process(
            TimestampRequest request,
            byte[] encodedResponse,
            String providerId,
            Set<TrustAnchor> trustAnchors) {
        if (trustAnchors == null || trustAnchors.isEmpty()) {
            throw new TimestampException("TSA_TRUST_NOT_CONFIGURED", "TSA güven kökü yapılandırılmamış.");
        }
        try {
            var generator = new TimeStampRequestGenerator();
            generator.setCertReq(true);
            if (request.requestedPolicyOid() != null && !request.requestedPolicyOid().isBlank()) {
                generator.setReqPolicy(new ASN1ObjectIdentifier(request.requestedPolicyOid()));
            }
            var bcRequest = generator.generate(
                    new ASN1ObjectIdentifier(request.digestAlgorithmOid()),
                    request.messageImprint(),
                    request.nonce());
            var response = new org.bouncycastle.tsp.TimeStampResponse(encodedResponse);
            response.validate(bcRequest);
            if (response.getStatus() != 0 && response.getStatus() != 1) {
                throw new TimestampException("TSA_REJECTED", "TSA isteği reddetti.");
            }
            var token = response.getTimeStampToken();
            if (token == null) {
                throw new TimestampException("TIMESTAMP_TOKEN_MISSING", "TSA cevabında zaman damgası tokenı yok.");
            }
            return tokenVerifier.verify(
                    token.getEncoded(),
                    request.digestAlgorithmOid(),
                    request.messageImprint(),
                    request.nonce(),
                    request.requestedPolicyOid(),
                    providerId,
                    trustAnchors);
        } catch (TimestampException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TimestampException(
                    "TIMESTAMP_INVALID",
                    "RFC 3161 cevabı istek, imza veya güven zinciri kontrolünden geçemedi.",
                    exception);
        }
    }

}
