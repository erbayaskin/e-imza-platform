package io.github.erbayaskin.eimza.api.signing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signing-configuration")
class SigningConfigurationController {
    private final ManifestSigner signer;

    SigningConfigurationController(ManifestSigner signer) {
        this.signer = signer;
    }

    @GetMapping("/manifest-key")
    ManifestKeyResponse manifestKey() {
        return new ManifestKeyResponse("Ed25519", signer.keyId(), signer.publicKey());
    }

    record ManifestKeyResponse(String algorithm, String keyId, String publicKey) {}
}
