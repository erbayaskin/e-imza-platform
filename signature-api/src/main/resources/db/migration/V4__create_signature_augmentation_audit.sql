CREATE TABLE signature_augmentation (
    id UUID PRIMARY KEY,
    source_digest VARCHAR(64) NOT NULL,
    result_digest VARCHAR(64) NOT NULL,
    target_level VARCHAR(20) NOT NULL,
    certificate_count INTEGER NOT NULL,
    revocation_value_count INTEGER NOT NULL,
    archive_timestamp_count INTEGER NOT NULL,
    archive_timestamp_policy_oid VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_signature_augmentation_source
    ON signature_augmentation (source_digest, created_at);
