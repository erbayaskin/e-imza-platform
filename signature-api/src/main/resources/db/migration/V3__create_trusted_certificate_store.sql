CREATE TABLE trusted_certificate (
    id UUID PRIMARY KEY,
    fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
    subject_dn VARCHAR(2000) NOT NULL,
    issuer_dn VARCHAR(2000) NOT NULL,
    serial_number_hex VARCHAR(256) NOT NULL,
    not_before TIMESTAMP WITH TIME ZONE NOT NULL,
    not_after TIMESTAMP WITH TIME ZONE NOT NULL,
    certificate_base64 TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE trust_store_lock (
    id INTEGER PRIMARY KEY,
    lock_version BIGINT NOT NULL
);

INSERT INTO trust_store_lock (id, lock_version) VALUES (1, 0);

CREATE TABLE trust_store_entry (
    id UUID PRIMARY KEY,
    trust_store_version_id UUID NOT NULL,
    trusted_certificate_id UUID NOT NULL,
    trust_type VARCHAR(20) NOT NULL,
    display_name VARCHAR(250) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_trust_store_entry_version
        FOREIGN KEY (trust_store_version_id) REFERENCES trust_store_version (id),
    CONSTRAINT fk_trust_store_entry_certificate
        FOREIGN KEY (trusted_certificate_id) REFERENCES trusted_certificate (id),
    CONSTRAINT uq_trust_store_entry_certificate
        UNIQUE (trust_store_version_id, trusted_certificate_id)
);

CREATE INDEX ix_trust_store_version_effective
    ON trust_store_version (valid_from, valid_until, status);

CREATE INDEX ix_trust_store_entry_version
    ON trust_store_entry (trust_store_version_id, enabled);
