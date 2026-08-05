package io.github.erbayaskin.eimza.smartcard.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;

class LoopbackRequestFilterTest {

    private LoopbackRequestFilter filter;

    @BeforeEach
    void setUp() {
        var properties = new AgentProperties();
        properties.setAllowedOrigins(List.of("https://portal.example.test"));
        filter = new LoopbackRequestFilter(properties);
    }

    @Test
    void rejectsRemoteAddressAndUnlistedOrigin() throws Exception {
        var remote = request("POST");
        remote.setRemoteAddr("192.168.1.10");
        remote.addHeader("X-EImza-Agent", "1");
        assertThat(execute(remote).status()).isEqualTo(403);

        var badOrigin = request("POST");
        badOrigin.addHeader("Origin", "https://attacker.example");
        badOrigin.addHeader("X-EImza-Agent", "1");
        assertThat(execute(badOrigin).status()).isEqualTo(403);
    }

    @Test
    void requiresAgentHeaderForPostAndAllowsConfiguredLoopbackCall() throws Exception {
        assertThat(execute(request("POST")).status()).isEqualTo(403);

        var allowed = request("POST");
        allowed.addHeader("Origin", "https://portal.example.test");
        allowed.addHeader("X-EImza-Agent", "1");
        var result = execute(allowed);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.chainCalled()).isTrue();
        assertThat(result.response().getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("https://portal.example.test");
    }

    private FilterResult execute(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> called.set(true);
        filter.doFilter(request, response, chain);
        return new FilterResult(response.getStatus(), called.get(), response);
    }

    private static MockHttpServletRequest request(String method) {
        var request = new MockHttpServletRequest(method, "/api/v1/cards");
        request.setRemoteAddr("127.0.0.1");
        request.setServerName("127.0.0.1");
        return request;
    }

    private record FilterResult(
            int status, boolean chainCalled, MockHttpServletResponse response) {}
}
