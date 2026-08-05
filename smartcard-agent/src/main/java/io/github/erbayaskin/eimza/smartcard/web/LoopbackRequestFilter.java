package io.github.erbayaskin.eimza.smartcard.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.github.erbayaskin.eimza.smartcard.config.AgentProperties;

@Component
public class LoopbackRequestFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;

    public LoopbackRequestFilter(AgentProperties properties) {
        this.allowedOrigins = Set.copyOf(properties.getAllowedOrigins());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isLoopback(request.getRemoteAddr()) || !isLoopbackHost(request.getServerName())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        var origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-EImza-Agent");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (HttpMethod.POST.matches(request.getMethod())
                && !"1".equals(request.getHeader("X-EImza-Agent"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "127.0.0.1".equals(host) || "::1".equals(host) || "localhost".equalsIgnoreCase(host);
    }
}
