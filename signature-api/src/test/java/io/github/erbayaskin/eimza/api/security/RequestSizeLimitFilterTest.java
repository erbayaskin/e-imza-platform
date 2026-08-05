package io.github.erbayaskin.eimza.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTest {

    @Test
    void rejectsDeclaredBodyLargerThanConfiguredLimit() throws Exception {
        var properties = new ApiSecurityProperties();
        properties.setMaximumRequestBytes(10);
        var filter = new RequestSizeLimitFilter(properties);
        var request = new MockHttpServletRequest("POST", "/api/v1/validations/signatures");
        request.setContent(new byte[11]);
        var response = new MockHttpServletResponse();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("oversized request must not reach controller");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
    }
}
