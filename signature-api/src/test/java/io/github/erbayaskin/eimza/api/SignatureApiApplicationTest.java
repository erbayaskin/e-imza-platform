package io.github.erbayaskin.eimza.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class SignatureApiApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void startsApplicationContextWithMigrations() {
        assertThat(applicationContext).isNotNull();
    }
}
