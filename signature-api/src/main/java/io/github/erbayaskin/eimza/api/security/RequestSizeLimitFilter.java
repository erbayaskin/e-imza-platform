package io.github.erbayaskin.eimza.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maximumRequestBytes;

    public RequestSizeLimitFilter(ApiSecurityProperties properties) {
        this.maximumRequestBytes = properties.getMaximumRequestBytes();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var contentLength = request.getContentLengthLong();
        if (contentLength > maximumRequestBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter()
                    .write(
                            "{\"title\":\"REQUEST_TOO_LARGE\",\"status\":413,"
                                    + "\"code\":\"REQUEST_TOO_LARGE\","
                                    + "\"detail\":\"İstek gövdesi izin verilen sınırı aşıyor.\","
                                    + "\"maximumRequestBytes\":"
                                    + maximumRequestBytes
                                    + "}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
