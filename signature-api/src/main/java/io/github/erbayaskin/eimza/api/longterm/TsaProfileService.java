package io.github.erbayaskin.eimza.api.longterm;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Service
public class TsaProfileService {
    private final TsaProfileRepository repository;
    private final TimestampProperties properties;
    private final Clock clock;

    public TsaProfileService(
            TsaProfileRepository repository, TimestampProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TsaProfileResponse current() {
        return repository.findFirstByOrderByVersionNumberDesc()
                .map(TsaProfileResponse::from)
                .orElseGet(this::configurationFallback);
    }

    @Transactional
    public TsaProfileResponse update(UpdateTsaProfileRequest request) {
        validateEndpoint(request.endpoint());
        var version = repository.findFirstByOrderByVersionNumberDesc()
                .map(value -> value.versionNumber() + 1).orElse(1L);
        return TsaProfileResponse.from(repository.save(
                TsaProfileEntity.create(version, request, clock.instant())));
    }

    private TsaProfileResponse configurationFallback() {
        return new TsaProfileResponse(
                0, properties.getProviderId(),
                properties.getEndpoint() == null ? "" : properties.getEndpoint(),
                Math.toIntExact(properties.getRequestTimeout().toSeconds()),
                null, properties.getArchivePolicyOid(), properties.isEnabled(),
                Instant.EPOCH, "APPLICATION_CONFIGURATION");
    }

    private static void validateEndpoint(String endpoint) {
        try {
            var uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "TSA_ENDPOINT_INVALID",
                    "TSA endpoint geçerli bir HTTP/HTTPS URI olmalıdır.", false);
        }
    }
}
