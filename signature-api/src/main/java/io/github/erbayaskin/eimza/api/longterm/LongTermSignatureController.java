package io.github.erbayaskin.eimza.api.longterm;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signatures/cades")
public class LongTermSignatureController {

    private final LongTermSignatureService service;

    public LongTermSignatureController(LongTermSignatureService service) {
        this.service = service;
    }

    @PostMapping("/augmentations")
    LongTermAugmentationResponse augment(
            @Valid @RequestBody LongTermAugmentationRequest request) {
        return service.augment(request);
    }
}
