package io.github.erbayaskin.eimza.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import io.github.erbayaskin.eimza.api.error.ApiException;

class TenantAccessTest {

    private final ApiSecurityProperties properties = new ApiSecurityProperties();
    private final TenantAccess tenantAccess = new TenantAccess(properties);

    @Test
    void acceptsTenantBoundToJwtClaim() {
        var tenantId = UUID.randomUUID();

        assertThatCode(() -> tenantAccess.requireRequestedTenant(tenantId, jwt(tenantId.toString())))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCrossTenantRequest() {
        var requestedTenant = UUID.randomUUID();

        assertThatThrownBy(() ->
                        tenantAccess.requireRequestedTenant(requestedTenant, jwt(UUID.randomUUID().toString())))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var apiError = (ApiException) error;
                    org.assertj.core.api.Assertions.assertThat(apiError.code())
                            .isEqualTo("TENANT_SCOPE_VIOLATION");
                    org.assertj.core.api.Assertions.assertThat(apiError.status().value()).isEqualTo(403);
                });
    }

    @Test
    void rejectsMissingOrMalformedTenantClaim() {
        assertThatThrownBy(() -> tenantAccess.requireRequestedTenant(UUID.randomUUID(), jwt(null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> tenantAccess.requireRequestedTenant(UUID.randomUUID(), jwt("not-a-uuid")))
                .isInstanceOf(ApiException.class);
    }

    private static JwtAuthenticationToken jwt(String tenantClaim) {
        var builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (tenantClaim != null) {
            builder.claim("tenant_id", tenantClaim);
        }
        return new JwtAuthenticationToken(builder.build());
    }
}
