package io.github.erbayaskin.eimza.api.validation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/validations")
public class ValidationController {

    private final ValidationService service;

    public ValidationController(ValidationService service) {
        this.service = service;
    }

    @PostMapping("/certificates")
    ValidationReport validateCertificate(@Valid @RequestBody CertificateValidationApiRequest request) {
        return service.validateCertificate(request);
    }

    @PostMapping("/signatures")
    ValidationReport validateSignature(@Valid @RequestBody SignatureValidationApiRequest request) {
        return service.validateSignature(request);
    }
}
