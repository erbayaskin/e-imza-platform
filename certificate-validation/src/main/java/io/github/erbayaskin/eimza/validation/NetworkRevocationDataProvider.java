package io.github.erbayaskin.eimza.validation;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.cert.ocsp.jcajce.JcaCertificateID;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

public class NetworkRevocationDataProvider implements RevocationDataProvider {

    private static final int MAXIMUM_EVIDENCE_SIZE = 5 * 1024 * 1024;
    private static final String OCSP_SIGNING_EKU = "1.3.6.1.5.5.7.3.9";
    private final HttpClient httpClient;
    private final Duration timeout;
    private final Duration maximumAge;
    private final Duration clockSkew;
    private final SecureRandom secureRandom;

    public NetworkRevocationDataProvider(
            HttpClient httpClient,
            Duration timeout,
            Duration maximumAge,
            Duration clockSkew) {
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("İptal kanıtı HTTP istemcisi yönlendirme izlememelidir.");
        }
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.maximumAge = maximumAge;
        this.clockSkew = clockSkew;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public RevocationEvidence resolve(
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime) {
        for (var uri : ocspUris(certificate)) {
            try {
                return queryOcsp(uri, certificate, issuer, validationTime);
            } catch (Exception ignored) {
                // Kontrollü CRL geri dönüşü denenir.
            }
        }
        for (var uri : crlUris(certificate)) {
            try {
                return queryCrl(uri, certificate, issuer, validationTime);
            } catch (Exception ignored) {
                // Sonraki dağıtım noktası denenir.
            }
        }
        return new RevocationEvidence(
                RevocationStatus.UNAVAILABLE,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                new byte[0]);
    }

    private RevocationEvidence queryOcsp(
            URI uri,
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime) throws Exception {
        validatePublicEndpoint(uri);
        var digestProvider = new JcaDigestCalculatorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build();
        var certificateId = new JcaCertificateID(
                digestProvider.get(CertificateID.HASH_SHA1),
                issuer,
                certificate.getSerialNumber());
        var nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        var extensions = new ExtensionsGenerator();
        extensions.addExtension(
                OCSPObjectIdentifiers.id_pkix_ocsp_nonce,
                false,
                new DEROctetString(nonce));
        var request = new OCSPReqBuilder()
                .addRequest(certificateId)
                .setRequestExtensions(extensions.generate())
                .build()
                .getEncoded();
        var encoded = send(
                HttpRequest.newBuilder(uri)
                        .timeout(timeout)
                        .header("Content-Type", "application/ocsp-request")
                        .header("Accept", "application/ocsp-response")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(request))
                        .build(),
                "application/ocsp-response");
        return evaluateOcsp(encoded, certificate, issuer, validationTime, nonce, uri.toString());
    }

    public RevocationEvidence evaluateOcsp(
            byte[] encoded,
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime) throws Exception {
        return evaluateOcsp(encoded, certificate, issuer, validationTime, null, "embedded");
    }

    private RevocationEvidence evaluateOcsp(
            byte[] encoded,
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime,
            byte[] expectedNonce,
            String sourceUri) throws Exception {
        var digestProvider = new JcaDigestCalculatorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build();
        var certificateId = new JcaCertificateID(
                digestProvider.get(CertificateID.HASH_SHA1),
                issuer,
                certificate.getSerialNumber());
        var response = new OCSPResp(encoded);
        if (response.getStatus() != OCSPResp.SUCCESSFUL
                || !(response.getResponseObject() instanceof BasicOCSPResp basic)) {
            throw new IllegalArgumentException("OCSP cevabı başarılı değil.");
        }
        if (expectedNonce != null) {
            validateOcspNonce(basic, expectedNonce);
        }
        var responder = findAuthorizedResponder(basic, issuer);
        if (!basic.isSignatureValid(new JcaContentVerifierProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(responder.getPublicKey()))) {
            throw new IllegalArgumentException("OCSP imzası geçersiz.");
        }
        var single = Arrays.stream(basic.getResponses())
                .filter(item -> item.getCertID().equals(certificateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("OCSP cevabı hedef sertifikayı içermiyor."));
        validateEvidenceTimes(
                basic.getProducedAt().toInstant(),
                single.getThisUpdate().toInstant(),
                single.getNextUpdate() == null ? null : single.getNextUpdate().toInstant(),
                validationTime);
        var status = single.getCertStatus();
        Instant revocationTime = null;
        RevocationStatus mapped;
        if (status == CertificateStatus.GOOD) {
            mapped = RevocationStatus.GOOD;
        } else if (status instanceof RevokedStatus revoked) {
            revocationTime = revoked.getRevocationTime().toInstant();
            mapped = revocationTime.isAfter(validationTime)
                    ? RevocationStatus.GOOD
                    : RevocationStatus.REVOKED;
        } else if (status instanceof UnknownStatus) {
            mapped = RevocationStatus.UNKNOWN;
        } else {
            mapped = RevocationStatus.UNKNOWN;
        }
        return new RevocationEvidence(
                mapped,
                "OCSP",
                basic.getProducedAt().toInstant(),
                single.getThisUpdate().toInstant(),
                single.getNextUpdate() == null ? null : single.getNextUpdate().toInstant(),
                revocationTime,
                sourceUri,
                encoded);
    }

    private RevocationEvidence queryCrl(
            URI uri,
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime) throws Exception {
        validatePublicEndpoint(uri);
        var encoded = send(
                HttpRequest.newBuilder(uri)
                        .timeout(timeout)
                        .header("Accept", "application/pkix-crl, application/x-pkcs7-crl")
                        .GET()
                        .build(),
                null);
        return evaluateCrl(encoded, certificate, issuer, validationTime, uri.toString());
    }

    public RevocationEvidence evaluateCrl(
            byte[] encoded,
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime) throws Exception {
        return evaluateCrl(encoded, certificate, issuer, validationTime, "embedded");
    }

    private RevocationEvidence evaluateCrl(
            byte[] encoded,
            X509Certificate certificate,
            X509Certificate issuer,
            Instant validationTime,
            String sourceUri) throws Exception {
        var crl = (X509CRL) CertificateFactory.getInstance("X.509")
                .generateCRL(new java.io.ByteArrayInputStream(encoded));
        if (!crl.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
            throw new IllegalArgumentException("SİL issuer değeri sertifika issuer ile eşleşmiyor.");
        }
        crl.verify(issuer.getPublicKey());
        validateEvidenceTimes(
                crl.getThisUpdate().toInstant(),
                crl.getThisUpdate().toInstant(),
                crl.getNextUpdate() == null ? null : crl.getNextUpdate().toInstant(),
                validationTime);
        var entry = crl.getRevokedCertificate(certificate.getSerialNumber());
        var revocationTime = entry == null ? null : entry.getRevocationDate().toInstant();
        var status = revocationTime != null && !revocationTime.isAfter(validationTime)
                ? RevocationStatus.REVOKED
                : RevocationStatus.GOOD;
        return new RevocationEvidence(
                status,
                "CRL",
                crl.getThisUpdate().toInstant(),
                crl.getThisUpdate().toInstant(),
                crl.getNextUpdate() == null ? null : crl.getNextUpdate().toInstant(),
                revocationTime,
                sourceUri,
                encoded);
    }

    private X509Certificate findAuthorizedResponder(
            BasicOCSPResp response,
            X509Certificate issuer) throws Exception {
        var candidates = new ArrayList<X509Certificate>();
        candidates.add(issuer);
        var converter = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
        for (X509CertificateHolder holder : response.getCerts()) {
            candidates.add(converter.getCertificate(holder));
        }
        for (var candidate : candidates) {
            if (!response.isSignatureValid(new JcaContentVerifierProviderBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(candidate.getPublicKey()))) {
                continue;
            }
            if (candidate.equals(issuer)) {
                return candidate;
            }
            candidate.verify(issuer.getPublicKey());
            var eku = candidate.getExtendedKeyUsage();
            if (eku != null && eku.contains(OCSP_SIGNING_EKU)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Yetkili OCSP cevaplayıcı sertifikası bulunamadı.");
    }

    private void validateEvidenceTimes(
            Instant producedAt,
            Instant thisUpdate,
            Instant nextUpdate,
            Instant validationTime) {
        if (producedAt.isAfter(validationTime.plus(clockSkew))
                || thisUpdate.isAfter(validationTime.plus(clockSkew))
                || producedAt.isBefore(validationTime.minus(maximumAge))
                || thisUpdate.isBefore(validationTime.minus(maximumAge))
                || (nextUpdate != null && nextUpdate.isBefore(validationTime.minus(clockSkew)))) {
            throw new IllegalArgumentException("İptal kanıtı tazelik penceresinin dışında.");
        }
    }

    private static void validateOcspNonce(BasicOCSPResp response, byte[] expected) throws Exception {
        var extension = response.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
        if (extension == null) {
            return;
        }
        var actual = ASN1OctetString.getInstance(extension.getParsedValue()).getOctets();
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("OCSP nonce eşleşmiyor.");
        }
    }

    private byte[] send(HttpRequest request, String expectedContentType) throws Exception {
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("İptal servisi HTTP hatası.");
        }
        if (expectedContentType != null) {
            var contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith(expectedContentType)) {
                throw new IllegalArgumentException("İptal servisi Content-Type değeri geçersiz.");
            }
        }
        try (InputStream stream = response.body()) {
            var encoded = stream.readNBytes(MAXIMUM_EVIDENCE_SIZE + 1);
            if (encoded.length == 0 || encoded.length > MAXIMUM_EVIDENCE_SIZE) {
                throw new IllegalArgumentException("İptal kanıtı boyutu geçersiz.");
            }
            return encoded;
        }
    }

    static void validatePublicEndpoint(URI uri) throws Exception {
        if (uri == null
                || (!"http".equalsIgnoreCase(uri.getScheme())
                        && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("İptal servisi URI değeri geçersiz.");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IllegalArgumentException("İptal servisi özel/ağ içi adrese çözümleniyor.");
            }
        }
    }

    static List<URI> ocspUris(X509Certificate certificate) {
        try {
            var encoded = certificate.getExtensionValue(Extension.authorityInfoAccess.getId());
            if (encoded == null) {
                return List.of();
            }
            var aia = AuthorityInformationAccess.getInstance(
                    ASN1OctetString.getInstance(encoded).getOctets());
            var result = new ArrayList<URI>();
            for (var description : aia.getAccessDescriptions()) {
                if (AccessDescription.id_ad_ocsp.equals(description.getAccessMethod())
                        && description.getAccessLocation().getTagNo() == GeneralName.uniformResourceIdentifier) {
                    result.add(URI.create(description.getAccessLocation().getName().toString()));
                }
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    static List<URI> crlUris(X509Certificate certificate) {
        try {
            var encoded = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId());
            if (encoded == null) {
                return List.of();
            }
            var points = CRLDistPoint.getInstance(
                    ASN1OctetString.getInstance(encoded).getOctets());
            var result = new ArrayList<URI>();
            for (var point : points.getDistributionPoints()) {
                var name = point.getDistributionPoint();
                if (name == null || name.getType() != DistributionPointName.FULL_NAME) {
                    continue;
                }
                for (var generalName : GeneralNames.getInstance(name.getName()).getNames()) {
                    if (generalName.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        result.add(URI.create(generalName.getName().toString()));
                    }
                }
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }
}
