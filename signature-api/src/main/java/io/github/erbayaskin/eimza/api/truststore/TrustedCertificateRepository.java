package io.github.erbayaskin.eimza.api.truststore;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TrustedCertificateRepository extends JpaRepository<TrustedCertificateEntity, UUID> {

    Optional<TrustedCertificateEntity> findByFingerprintSha256(String fingerprintSha256);
}
