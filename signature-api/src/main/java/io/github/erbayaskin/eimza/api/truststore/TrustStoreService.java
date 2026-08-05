package io.github.erbayaskin.eimza.api.truststore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.erbayaskin.eimza.api.error.ApiException;

@Service
public class TrustStoreService {

    private final TrustStoreLockRepository lockRepository;
    private final TrustStoreVersionRepository versionRepository;
    private final TrustedCertificateRepository certificateRepository;
    private final TrustStoreEntryRepository entryRepository;
    private final TrustedCertificateParser certificateParser;
    private final Clock clock;

    public TrustStoreService(
            TrustStoreLockRepository lockRepository,
            TrustStoreVersionRepository versionRepository,
            TrustedCertificateRepository certificateRepository,
            TrustStoreEntryRepository entryRepository,
            TrustedCertificateParser certificateParser,
            Clock clock) {
        this.lockRepository = lockRepository;
        this.versionRepository = versionRepository;
        this.certificateRepository = certificateRepository;
        this.entryRepository = entryRepository;
        this.certificateParser = certificateParser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrustStoreSnapshotResponse current() {
        return versionRepository.findFirstByStatusOrderByValidFromDesc("ACTIVE")
                .map(this::toResponse)
                .orElseGet(TrustStoreSnapshotResponse::empty);
    }

    @Transactional(readOnly = true)
    public TrustStoreSnapshotResponse version(UUID versionId) {
        return versionRepository.findById(versionId)
                .map(this::toResponse)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "TRUST_STORE_VERSION_NOT_FOUND",
                                        "Güven deposu sürümü bulunamadı.",
                                        false));
    }

    @Transactional
    public TrustStoreSnapshotResponse add(CreateTrustedCertificateRequest request) {
        acquireMutationLock();
        var parsed = certificateParser.parse(request.certificate());
        var current = activeVersion();
        var now = nextMutationTime(current);
        var certificate =
                certificateRepository.findByFingerprintSha256(parsed.fingerprintSha256())
                        .orElseGet(
                                () ->
                                        certificateRepository.save(
                                                TrustedCertificateEntity.create(
                                                        UUID.randomUUID(), parsed, now)));
        var specifications = specifications(current);
        if (specifications.stream()
                .anyMatch(specification -> specification.certificate().id().equals(certificate.id()))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TRUSTED_CERTIFICATE_ALREADY_ACTIVE",
                    "Sertifika güncel güven deposunda zaten bulunuyor.",
                    false);
        }
        specifications.add(
                new EntrySpecification(
                        certificate,
                        request.trustType(),
                        request.displayName(),
                        request.enabled()));
        return publish(current, specifications, now);
    }

    @Transactional
    public TrustStoreSnapshotResponse update(
            UUID certificateId,
            UpdateTrustedCertificateRequest request) {
        acquireMutationLock();
        var current = requireActiveVersion();
        var now = nextMutationTime(current);
        var specifications = specifications(current);
        var found = false;
        var updated = new ArrayList<EntrySpecification>(specifications.size());
        for (var specification : specifications) {
            if (specification.certificate().id().equals(certificateId)) {
                updated.add(
                        new EntrySpecification(
                                specification.certificate(),
                                request.trustType(),
                                request.displayName(),
                                request.enabled()));
                found = true;
            } else {
                updated.add(specification);
            }
        }
        if (!found) {
            throw certificateNotFound();
        }
        return publish(current, updated, now);
    }

    @Transactional
    public TrustStoreSnapshotResponse remove(UUID certificateId) {
        acquireMutationLock();
        var current = requireActiveVersion();
        var now = nextMutationTime(current);
        var specifications = specifications(current);
        var removed = specifications.removeIf(
                specification -> specification.certificate().id().equals(certificateId));
        if (!removed) {
            throw certificateNotFound();
        }
        return publish(current, specifications, now);
    }

    private TrustStoreSnapshotResponse publish(
            TrustStoreVersionEntity current,
            List<EntrySpecification> specifications,
            Instant now) {
        if (current != null) {
            current.supersede(now);
        }
        var version =
                TrustStoreVersionEntity.active(
                        UUID.randomUUID(),
                        createVersionName(now),
                        contentDigest(specifications),
                        now);
        versionRepository.save(version);
        var entries =
                specifications.stream()
                        .map(
                                specification ->
                                        TrustStoreEntryEntity.create(
                                                UUID.randomUUID(),
                                                version,
                                                specification.certificate(),
                                                specification.trustType(),
                                                specification.displayName(),
                                                specification.enabled(),
                                                now))
                        .toList();
        entryRepository.saveAll(entries);
        return new TrustStoreSnapshotResponse(
                version.version(),
                version.contentDigest(),
                version.validFrom(),
                version.validUntil(),
                entries.stream().map(TrustedCertificateResponse::from).toList());
    }

    private TrustStoreSnapshotResponse toResponse(TrustStoreVersionEntity version) {
        var certificates =
                entryRepository.findByTrustStoreVersion_IdOrderByDisplayName(version.id()).stream()
                        .map(TrustedCertificateResponse::from)
                        .toList();
        return new TrustStoreSnapshotResponse(
                version.version(),
                version.contentDigest(),
                version.validFrom(),
                version.validUntil(),
                certificates);
    }

    private List<EntrySpecification> specifications(TrustStoreVersionEntity version) {
        if (version == null) {
            return new ArrayList<>();
        }
        return entryRepository.findByTrustStoreVersion_IdOrderByDisplayName(version.id()).stream()
                .map(
                        entry ->
                                new EntrySpecification(
                                        entry.certificate(),
                                        entry.trustType(),
                                        entry.displayName(),
                                        entry.enabled()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private String contentDigest(List<EntrySpecification> specifications) {
        var canonical =
                specifications.stream()
                        .sorted(
                                Comparator.comparing(
                                        specification ->
                                                specification.certificate().fingerprintSha256()))
                        .map(
                                specification ->
                                        String.join(
                                                "|",
                                                specification.certificate().fingerprintSha256(),
                                                specification.trustType().name(),
                                                specification.displayName(),
                                                Boolean.toString(specification.enabled())))
                        .reduce("", (left, right) -> left + right + "\n");
        try {
            return HexFormat.of()
                    .withUpperCase()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM SHA-256 algoritmasını sağlamıyor.", exception);
        }
    }

    private String createVersionName(Instant now) {
        return now.toEpochMilli()
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private Instant mutationTime() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private Instant nextMutationTime(TrustStoreVersionEntity current) {
        var now = mutationTime();
        if (current != null && !now.isAfter(current.validFrom())) {
            return current.validFrom().plusMillis(1);
        }
        return now;
    }

    private void acquireMutationLock() {
        if (lockRepository.lockById(1) == null) {
            throw new IllegalStateException("Güven deposu mutasyon kilidi bulunamadı.");
        }
    }

    private TrustStoreVersionEntity activeVersion() {
        return versionRepository.findFirstByStatusOrderByValidFromDesc("ACTIVE").orElse(null);
    }

    private TrustStoreVersionEntity requireActiveVersion() {
        var current = activeVersion();
        if (current == null) {
            throw certificateNotFound();
        }
        return current;
    }

    private ApiException certificateNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "TRUSTED_CERTIFICATE_NOT_FOUND",
                "Sertifika güncel güven deposunda bulunamadı.",
                false);
    }

    private record EntrySpecification(
            TrustedCertificateEntity certificate,
            TrustedCertificateType trustType,
            String displayName,
            boolean enabled) {
    }
}
