package io.github.erbayaskin.eimza.timestamp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.TrustAnchor;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.tsp.TimeStampRequestGenerator;

public class Rfc3161TimestampClient implements TimestampClient {

    private static final int MAXIMUM_RESPONSE_SIZE = 1024 * 1024;
    private final URI endpoint;
    private final String providerId;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final Map<String, String> headers;
    private final Set<TrustAnchor> trustAnchors;
    private final TimestampResponseProcessor processor;

    public Rfc3161TimestampClient(
            URI endpoint,
            String providerId,
            HttpClient httpClient,
            Duration requestTimeout,
            Map<String, String> headers,
            Set<TrustAnchor> trustAnchors) {
        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("Üretim TSA adresi HTTPS olmalıdır.");
        }
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("TSA adresi geçerli bir host içermeli ve user-info içermemelidir.");
        }
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("TSA HTTP istemcisi yönlendirmeleri takip etmemelidir.");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("TSA istek zaman aşımı pozitif olmalıdır.");
        }
        this.endpoint = endpoint;
        this.providerId = providerId;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.headers = Map.copyOf(headers);
        this.trustAnchors = Set.copyOf(trustAnchors);
        this.processor = new TimestampResponseProcessor();
    }

    @Override
    public TimestampResponse timestamp(TimestampRequest request) {
        try {
            var generator = new TimeStampRequestGenerator();
            generator.setCertReq(true);
            if (request.requestedPolicyOid() != null && !request.requestedPolicyOid().isBlank()) {
                generator.setReqPolicy(new ASN1ObjectIdentifier(request.requestedPolicyOid()));
            }
            var encodedRequest = generator.generate(
                            new ASN1ObjectIdentifier(request.digestAlgorithmOid()),
                            request.messageImprint(),
                            request.nonce())
                    .getEncoded();
            var builder = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/timestamp-query")
                    .header("Accept", "application/timestamp-reply")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encodedRequest));
            headers.forEach(builder::header);
            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TimestampException("TSA_HTTP_ERROR", "TSA HTTP isteği başarısız oldu.");
            }
            var contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/timestamp-repl")) {
                throw new TimestampException(
                        "TSA_CONTENT_TYPE_INVALID",
                        "TSA cevabının Content-Type değeri zaman damgası yanıtı değil.");
            }
            if (response.body().length == 0 || response.body().length > MAXIMUM_RESPONSE_SIZE) {
                throw new TimestampException(
                        "TSA_RESPONSE_SIZE_INVALID",
                        "TSA cevabı boş veya izin verilen boyutu aşıyor.");
            }
            return processor.process(request, response.body(), providerId, trustAnchors);
        } catch (TimestampException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TimestampException("TSA_INTERRUPTED", "TSA isteği kesildi.", exception);
        } catch (Exception exception) {
            throw new TimestampException("TSA_UNAVAILABLE", "TSA servisine erişilemedi.", exception);
        }
    }
}
