package io.github.erbayaskin.eimza.api.validation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("eimza.validation")
public class ValidationProperties {

    private String policyVersion = "turkiye-baseline-v1";
    private int minimumRsaBits = 2048;
    private int minimumEcBits = 256;
    private boolean requireQcCompliance = true;
    private boolean revocationRequired = true;
    private Duration maximumRevocationAge = Duration.ofHours(24);
    private Duration clockSkew = Duration.ofMinutes(5);
    private Duration networkTimeout = Duration.ofSeconds(10);
    private List<String> requiredCertificatePolicyOids = new ArrayList<>();
    private String signaturePolicyOid = "";
    private String signaturePolicyDigestAlgorithmOid = "2.16.840.1.101.3.4.2.1";
    private String signaturePolicyDigest = "";
    private String signaturePolicyUri = "";

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String value) { this.policyVersion = value; }
    public int getMinimumRsaBits() { return minimumRsaBits; }
    public void setMinimumRsaBits(int value) { this.minimumRsaBits = value; }
    public int getMinimumEcBits() { return minimumEcBits; }
    public void setMinimumEcBits(int value) { this.minimumEcBits = value; }
    public boolean isRequireQcCompliance() { return requireQcCompliance; }
    public void setRequireQcCompliance(boolean value) { this.requireQcCompliance = value; }
    public boolean isRevocationRequired() { return revocationRequired; }
    public void setRevocationRequired(boolean value) { this.revocationRequired = value; }
    public Duration getMaximumRevocationAge() { return maximumRevocationAge; }
    public void setMaximumRevocationAge(Duration value) { this.maximumRevocationAge = value; }
    public Duration getClockSkew() { return clockSkew; }
    public void setClockSkew(Duration value) { this.clockSkew = value; }
    public Duration getNetworkTimeout() { return networkTimeout; }
    public void setNetworkTimeout(Duration value) { this.networkTimeout = value; }
    public List<String> getRequiredCertificatePolicyOids() { return requiredCertificatePolicyOids; }
    public void setRequiredCertificatePolicyOids(List<String> value) {
        this.requiredCertificatePolicyOids = value;
    }
    public String getSignaturePolicyOid() { return signaturePolicyOid; }
    public void setSignaturePolicyOid(String value) { this.signaturePolicyOid = value; }
    public String getSignaturePolicyDigestAlgorithmOid() { return signaturePolicyDigestAlgorithmOid; }
    public void setSignaturePolicyDigestAlgorithmOid(String value) {
        this.signaturePolicyDigestAlgorithmOid = value;
    }
    public String getSignaturePolicyDigest() { return signaturePolicyDigest; }
    public void setSignaturePolicyDigest(String value) { this.signaturePolicyDigest = value; }
    public String getSignaturePolicyUri() { return signaturePolicyUri; }
    public void setSignaturePolicyUri(String value) { this.signaturePolicyUri = value; }
}
