package io.github.erbayaskin.eimza.api.signing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("eimza.manifest")
public class ManifestSigningProperties {
    private String privateKey = "";
    private String publicKey = "";
    private String keyId = "local-ephemeral";
    private String localKeyPath = ".eimza/manifest-ed25519";

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getLocalKeyPath() { return localKeyPath; }
    public void setLocalKeyPath(String localKeyPath) { this.localKeyPath = localKeyPath; }
}
