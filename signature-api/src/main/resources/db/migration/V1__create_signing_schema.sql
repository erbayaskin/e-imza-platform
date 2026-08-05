CREATE TABLE signing_session (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_id VARCHAR(200) NOT NULL,
    device_id UUID NOT NULL,
    document_id UUID NOT NULL,
    format VARCHAR(16) NOT NULL,
    target_level VARCHAR(16) NOT NULL,
    turkish_profile VARCHAR(4) NOT NULL,
    status VARCHAR(40) NOT NULL,
    document_digest VARCHAR(128) NOT NULL,
    digest_algorithm VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_signing_session_idempotency
        UNIQUE (tenant_id, subject_id, idempotency_key)
);

CREATE INDEX ix_signing_session_tenant_status
    ON signing_session (tenant_id, status);

CREATE INDEX ix_signing_session_expires_at
    ON signing_session (expires_at);

CREATE TABLE signature_artifact (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    artifact_digest VARCHAR(128) NOT NULL,
    format VARCHAR(16) NOT NULL,
    signature_level VARCHAR(16) NOT NULL,
    policy_oid VARCHAR(200),
    signing_certificate_fingerprint VARCHAR(128) NOT NULL,
    storage_reference VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_signature_artifact_session
        FOREIGN KEY (session_id) REFERENCES signing_session (id)
);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(200) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    previous_event_hash VARCHAR(128),
    event_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_audit_event_tenant_created
    ON audit_event (tenant_id, created_at);
