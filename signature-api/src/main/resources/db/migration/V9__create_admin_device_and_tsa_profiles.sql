CREATE TABLE server_key_profile (
    id UUID PRIMARY KEY,
    server_key_id VARCHAR(36) NOT NULL UNIQUE,
    display_name VARCHAR(250) NOT NULL,
    device_type VARCHAR(20) NOT NULL,
    pkcs11_library VARCHAR(1000) NOT NULL,
    slot_list_index INTEGER,
    atr VARCHAR(256),
    atr_mask VARCHAR(256),
    certificate_fingerprint VARCHAR(128),
    credential_ref VARCHAR(250),
    allowed_tenant_ids TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE tsa_profile (
    id UUID PRIMARY KEY,
    version_number BIGINT NOT NULL UNIQUE,
    provider_id VARCHAR(250) NOT NULL,
    endpoint VARCHAR(1000) NOT NULL,
    request_timeout_seconds INTEGER NOT NULL,
    credential_ref VARCHAR(250),
    archive_policy_oid VARCHAR(200),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_server_key_profile_enabled
    ON server_key_profile (enabled);

CREATE INDEX ix_tsa_profile_version
    ON tsa_profile (version_number DESC);
