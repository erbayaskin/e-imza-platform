package io.github.erbayaskin.eimza.api.cardprofile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardProfileCatalogServiceTest {

    private final CardProfileCatalogService service = new CardProfileCatalogService();

    @Test
    void matchesAkisAtr() {
        var result = service.matchAtr("3B9F978131FE4580655443D3228231C073F621808105D3");

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("akis-smart-card");
        assertThat(result.defaultPkcs11Library()).endsWith("akisp11.dll");
    }

    @Test
    void doesNotMatchUnknownAtr() {
        assertThat(service.matchAtr("3B0000")).isNull();
    }

    @Test
    void appliesAtrMask() {
        assertThat(CardProfileCatalogService.atrMatches("3B12", "3B99", "FF00")).isTrue();
        assertThat(CardProfileCatalogService.atrMatches("3C12", "3B99", "FF00")).isFalse();
    }
}
