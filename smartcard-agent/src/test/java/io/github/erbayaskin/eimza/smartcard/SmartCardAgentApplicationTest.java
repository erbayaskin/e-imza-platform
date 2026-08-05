package io.github.erbayaskin.eimza.smartcard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "eimza.agent.ui.enabled=false")
class SmartCardAgentApplicationTest {

    @Test
    void applicationContextStartsWithSigningFailClosed() {}
}
