package io.github.erbayaskin.eimza.api.serversigning;

import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/admin/server-key-profiles")
public class ServerKeyProfileAdminController {
    private final ServerKeyProfileAdminService service;

    public ServerKeyProfileAdminController(ServerKeyProfileAdminService service) {
        this.service = service;
    }

    @GetMapping
    List<ServerKeyProfileResponse> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ServerKeyProfileResponse create(@Valid @RequestBody CreateServerKeyProfileRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    ServerKeyProfileResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateServerKeyProfileRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { service.delete(id); }
}
