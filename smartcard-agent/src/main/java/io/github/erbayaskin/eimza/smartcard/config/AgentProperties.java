package io.github.erbayaskin.eimza.smartcard.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eimza.agent")
public class AgentProperties {

    private String deviceId = "UNCONFIGURED";
    private String devicePrivateKey = "";
    private String devicePublicKey = "";
    private String manifestPublicKey = "";
    private List<String> allowedOrigins = new ArrayList<>();
    private List<Profile> cardProfiles = new ArrayList<>();

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDevicePrivateKey() {
        return devicePrivateKey;
    }

    public void setDevicePrivateKey(String devicePrivateKey) {
        this.devicePrivateKey = devicePrivateKey;
    }

    public String getDevicePublicKey() {
        return devicePublicKey;
    }

    public void setDevicePublicKey(String devicePublicKey) {
        this.devicePublicKey = devicePublicKey;
    }

    public String getManifestPublicKey() {
        return manifestPublicKey;
    }

    public void setManifestPublicKey(String manifestPublicKey) {
        this.manifestPublicKey = manifestPublicKey;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<Profile> getCardProfiles() {
        return cardProfiles;
    }

    public void setCardProfiles(List<Profile> cardProfiles) {
        this.cardProfiles = cardProfiles;
    }

    public static class Profile {
        private String id;
        private String displayName;
        private String deviceType = "SMART_CARD";
        private String atr;
        private String atrMask;
        private String pkcs11Library;
        private Integer slotListIndex;
        private Boolean autoDiscoverSlot;
        private List<String> allowedMechanisms = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        public String getAtr() { return atr; }
        public void setAtr(String atr) { this.atr = atr; }
        public String getAtrMask() { return atrMask; }
        public void setAtrMask(String atrMask) { this.atrMask = atrMask; }
        public String getPkcs11Library() { return pkcs11Library; }
        public void setPkcs11Library(String pkcs11Library) { this.pkcs11Library = pkcs11Library; }
        public Integer getSlotListIndex() { return slotListIndex; }
        public void setSlotListIndex(Integer slotListIndex) { this.slotListIndex = slotListIndex; }
        public Boolean getAutoDiscoverSlot() { return autoDiscoverSlot; }
        public void setAutoDiscoverSlot(Boolean autoDiscoverSlot) {
            this.autoDiscoverSlot = autoDiscoverSlot;
        }
        public List<String> getAllowedMechanisms() { return allowedMechanisms; }
        public void setAllowedMechanisms(List<String> allowedMechanisms) {
            this.allowedMechanisms = allowedMechanisms;
        }
    }
}
