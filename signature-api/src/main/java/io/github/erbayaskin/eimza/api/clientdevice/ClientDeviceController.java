package io.github.erbayaskin.eimza.api.clientdevice;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/client-devices")
public class ClientDeviceController {
    private final ClientDeviceService service;

    public ClientDeviceController(ClientDeviceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClientDeviceResponse register(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody RegisterClientDeviceRequest request) {
        return service.register(tenantId, request);
    }
}
