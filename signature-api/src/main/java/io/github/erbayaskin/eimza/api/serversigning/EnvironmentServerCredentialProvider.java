package io.github.erbayaskin.eimza.api.serversigning;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Component
class EnvironmentServerCredentialProvider implements ServerCredentialProvider {
    @Override
    public char[] resolve(String credentialRef) {
        var value = System.getenv(credentialRef);
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVER_CREDENTIAL_UNAVAILABLE",
                    "Sunucu imza kimlik bilgisi güvenli kaynaktan alınamadı.",
                    false);
        }
        return value.toCharArray();
    }
}
