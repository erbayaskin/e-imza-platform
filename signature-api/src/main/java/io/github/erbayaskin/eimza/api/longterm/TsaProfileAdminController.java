package io.github.erbayaskin.eimza.api.longterm;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tsa-profile")
public class TsaProfileAdminController {
    private final TsaProfileService service;

    public TsaProfileAdminController(TsaProfileService service) {
        this.service = service;
    }

    @GetMapping
    TsaProfileResponse current() { return service.current(); }

    @PutMapping
    TsaProfileResponse update(@Valid @RequestBody UpdateTsaProfileRequest request) {
        return service.update(request);
    }
}
