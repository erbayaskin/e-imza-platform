package io.github.erbayaskin.eimza.api.serversigning;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("eimza.server-signing")
public class ServerSigningProperties {
    private List<Profile> profiles = new ArrayList<>();

    public List<Profile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<Profile> profiles) {
        this.profiles = profiles == null ? new ArrayList<>() : profiles;
    }

    public static class Profile {
        private String serverKeyId;
        private ServerDeviceType deviceType;
        private String pkcs11Library;
        private Integer slotListIndex;
        private String atr;
        private String atrMask;
        private String certificateFingerprint;
        private String credentialRef;
        private List<String> allowedTenantIds = new ArrayList<>();

        public String getServerKeyId() { return serverKeyId; }
        public void setServerKeyId(String value) { this.serverKeyId = value; }
        public ServerDeviceType getDeviceType() { return deviceType; }
        public void setDeviceType(ServerDeviceType value) { this.deviceType = value; }
        public String getPkcs11Library() { return pkcs11Library; }
        public void setPkcs11Library(String value) { this.pkcs11Library = value; }
        public Integer getSlotListIndex() { return slotListIndex; }
        public void setSlotListIndex(Integer value) { this.slotListIndex = value; }
        public String getAtr() { return atr; }
        public void setAtr(String value) { this.atr = value; }
        public String getAtrMask() { return atrMask; }
        public void setAtrMask(String value) { this.atrMask = value; }
        public String getCertificateFingerprint() { return certificateFingerprint; }
        public void setCertificateFingerprint(String value) { this.certificateFingerprint = value; }
        public String getCredentialRef() { return credentialRef; }
        public void setCredentialRef(String value) { this.credentialRef = value; }
        public List<String> getAllowedTenantIds() { return allowedTenantIds; }
        public void setAllowedTenantIds(List<String> value) {
            this.allowedTenantIds = value == null ? new ArrayList<>() : value;
        }
    }
}
