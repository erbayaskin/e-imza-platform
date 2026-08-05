package io.github.erbayaskin.eimza.api.truststore;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/trusted-certificates")
public class TrustStoreController {

    private final TrustStoreService service;

    public TrustStoreController(TrustStoreService service) {
        this.service = service;
    }

    @GetMapping
    TrustStoreSnapshotResponse current() {
        return service.current();
    }

    @GetMapping("/versions/{versionId}")
    TrustStoreSnapshotResponse version(@PathVariable UUID versionId) {
        return service.version(versionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TrustStoreSnapshotResponse add(
            @Valid @RequestBody CreateTrustedCertificateRequest request) {
        return service.add(request);
    }

    @PutMapping("/{certificateId}")
    TrustStoreSnapshotResponse update(
            @PathVariable UUID certificateId,
            @Valid @RequestBody UpdateTrustedCertificateRequest request) {
        return service.update(certificateId, request);
    }

    @DeleteMapping("/{certificateId}")
    TrustStoreSnapshotResponse remove(@PathVariable UUID certificateId) {
        return service.remove(certificateId);
    }
}
