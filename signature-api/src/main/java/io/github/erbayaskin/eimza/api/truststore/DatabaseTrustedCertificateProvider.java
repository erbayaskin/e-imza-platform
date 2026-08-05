package io.github.erbayaskin.eimza.api.truststore;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.validation.TrustedCertificateProvider;

@Component
public class DatabaseTrustedCertificateProvider implements TrustedCertificateProvider {

    private final TrustStoreVersionRepository versionRepository;
    private final TrustStoreEntryRepository entryRepository;
    private final TrustedCertificateParser parser;

    public DatabaseTrustedCertificateProvider(
            TrustStoreVersionRepository versionRepository,
            TrustStoreEntryRepository entryRepository,
            TrustedCertificateParser parser) {
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.parser = parser;
    }

    @Override
    @Transactional(readOnly = true)
    public TrustedCertificateSnapshot snapshotAt(Instant validationTime) {
        var version =
                versionRepository.findEffectiveAt(validationTime)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Doğrulama zamanı için güven deposu sürümü yok: "
                                                        + validationTime));
        var certificates =
                entryRepository.findByTrustStoreVersion_IdOrderByDisplayName(version.id()).stream()
                        .filter(TrustStoreEntryEntity::enabled)
                        .map(
                                entry ->
                                        new TrustedCertificateProvider.TrustedCertificate(
                                                parser.decodeEntity(entry.certificate()),
                                                TrustedCertificateProvider.TrustedCertificateType.valueOf(
                                                        entry.trustType().name()),
                                                entry.certificate().fingerprintSha256()))
                        .toList();
        return new TrustedCertificateSnapshot(
                version.version(),
                version.validFrom(),
                version.validUntil(),
                certificates);
    }
}
