package io.github.erbayaskin.eimza.api.cardprofile;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/card-profile-catalog")
public class CardProfileCatalogController {

    private final CardProfileCatalogService service;

    public CardProfileCatalogController(CardProfileCatalogService service) {
        this.service = service;
    }

    @GetMapping
    List<CardProfileTemplate> templates() {
        return service.templates();
    }

    @GetMapping("/detected-cards")
    List<DetectedSmartCard> detectedCards() {
        return service.detectCards();
    }
}
