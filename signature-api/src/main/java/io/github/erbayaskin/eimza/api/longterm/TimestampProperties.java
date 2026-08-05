package io.github.erbayaskin.eimza.api.longterm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("eimza.timestamp")
public class TimestampProperties {

    private boolean enabled;
    private String endpoint;
    private String providerId = "configured-tsa";
    private Duration requestTimeout = Duration.ofSeconds(15);
    private String authorizationHeader;
    private String archivePolicyOid;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public String getAuthorizationHeader() { return authorizationHeader; }
    public void setAuthorizationHeader(String authorizationHeader) {
        this.authorizationHeader = authorizationHeader;
    }
    public String getArchivePolicyOid() { return archivePolicyOid; }
    public void setArchivePolicyOid(String archivePolicyOid) {
        this.archivePolicyOid = archivePolicyOid;
    }
}
