package io.github.erbayaskin.eimza.api.validation.policy;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/validation-policy")
public class ValidationPolicyConfigurationController {

    private final ValidationPolicyConfigurationService service;

    public ValidationPolicyConfigurationController(ValidationPolicyConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    ValidationPolicyConfigurationResponse current() {
        return service.current();
    }

    @PutMapping
    ValidationPolicyConfigurationResponse update(
            @Valid @RequestBody UpdateValidationPolicyRequest request) {
        return service.update(request);
    }
}
