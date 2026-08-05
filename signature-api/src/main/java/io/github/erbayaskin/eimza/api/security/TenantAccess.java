package io.github.erbayaskin.eimza.api.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Component
public class TenantAccess {

    private final String tenantClaim;

    public TenantAccess(ApiSecurityProperties properties) {
        this.tenantClaim = properties.getTenantClaim();
    }

    public void requireRequestedTenant(UUID requestedTenant, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return;
        }
        var claim = jwtAuthentication.getToken().getClaimAsString(tenantClaim);
        try {
            if (claim == null || !requestedTenant.equals(UUID.fromString(claim))) {
                throw violation();
            }
        } catch (IllegalArgumentException exception) {
            throw violation();
        }
    }

    private static ApiException violation() {
        return new ApiException(
                HttpStatus.FORBIDDEN,
                "TENANT_SCOPE_VIOLATION",
                "İstenen kiracı JWT kiracı kapsamıyla eşleşmiyor.",
                false);
    }
}
