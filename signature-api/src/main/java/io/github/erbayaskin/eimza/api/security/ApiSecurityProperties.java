package io.github.erbayaskin.eimza.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("eimza.security")
public class ApiSecurityProperties {

    private long maximumRequestBytes = 70L * 1024L * 1024L;
    private String tenantClaim = "tenant_id";

    public long getMaximumRequestBytes() {
        return maximumRequestBytes;
    }

    public void setMaximumRequestBytes(long maximumRequestBytes) {
        this.maximumRequestBytes = maximumRequestBytes;
    }

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }
}
