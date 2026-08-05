package io.github.erbayaskin.eimza.api.serversigning;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Service
public class ServerKeyProfileAdminService {
    private final ServerKeyProfileRepository repository;
    private final Clock clock;

    public ServerKeyProfileAdminService(ServerKeyProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ServerKeyProfileResponse> list() {
        return repository.findAllByOrderByDisplayNameAsc().stream()
                .map(ServerKeyProfileResponse::from).toList();
    }

    @Transactional
    public ServerKeyProfileResponse create(CreateServerKeyProfileRequest request) {
        validate(request.deviceType(), request.pkcs11Library(), request.slotListIndex(),
                request.atr(), request.atrMask(), request.credentialRef());
        return ServerKeyProfileResponse.from(repository.save(
                ServerKeyProfileEntity.create(UUID.randomUUID(), request, clock.instant())));
    }

    @Transactional
    public ServerKeyProfileResponse update(UUID id, UpdateServerKeyProfileRequest request) {
        validate(request.deviceType(), request.pkcs11Library(), request.slotListIndex(),
                request.atr(), request.atrMask(), request.credentialRef());
        var value = require(id);
        value.update(request, clock.instant());
        return ServerKeyProfileResponse.from(repository.save(value));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(require(id));
    }

    private ServerKeyProfileEntity require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "SERVER_KEY_PROFILE_NOT_FOUND",
                "Server-side anahtar profili bulunamadı.", false));
    }

    private static void validate(
            ServerDeviceType type, String library, Integer slot,
            String atr, String mask, String credentialRef) {
        if (!Path.of(library).isAbsolute()) {
            throw invalid("PKCS#11 kütüphane yolu mutlak olmalıdır.");
        }
        if (type == ServerDeviceType.HSM) {
            if (slot == null || credentialRef == null || credentialRef.isBlank()) {
                throw invalid("HSM için slot numarası ve credentialRef zorunludur.");
            }
            if (hasText(atr) || hasText(mask)) {
                throw invalid("HSM profiline ATR girilemez.");
            }
        } else {
            if (!hasText(atr) || !hasText(mask)) {
                throw invalid("Server-side akıllı kart için ATR ve ATR maskesi zorunludur.");
            }
            var normalizedAtr = atr.replaceAll("[^0-9A-Fa-f]", "");
            var normalizedMask = mask.replaceAll("[^0-9A-Fa-f]", "");
            if (normalizedAtr.length() % 2 != 0 || normalizedAtr.length() != normalizedMask.length()) {
                throw invalid("ATR ve ATR maskesi geçerli ve aynı uzunlukta hex olmalıdır.");
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, "SERVER_KEY_PROFILE_INVALID", message, false);
    }
}
