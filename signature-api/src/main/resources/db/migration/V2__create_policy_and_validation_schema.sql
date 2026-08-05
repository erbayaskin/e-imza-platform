CREATE TABLE policy_version (
    id UUID PRIMARY KEY,
    policy_type VARCHAR(60) NOT NULL,
    version VARCHAR(40) NOT NULL,
    content_digest VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    source_uri VARCHAR(1000),
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_policy_version UNIQUE (policy_type, version)
);

CREATE TABLE trust_store_version (
    id UUID PRIMARY KEY,
    version VARCHAR(40) NOT NULL UNIQUE,
    content_digest VARCHAR(128) NOT NULL,
    status VARCHAR(40) NOT NULL,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE validation_run (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_digest VARCHAR(128) NOT NULL,
    validation_time TIMESTAMP WITH TIME ZONE NOT NULL,
    policy_version_id UUID NOT NULL,
    trust_store_version_id UUID NOT NULL,
    main_indication VARCHAR(20) NOT NULL,
    qualification_result VARCHAR(40) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_validation_policy
        FOREIGN KEY (policy_version_id) REFERENCES policy_version (id),
    CONSTRAINT fk_validation_trust_store
        FOREIGN KEY (trust_store_version_id) REFERENCES trust_store_version (id)
);

CREATE TABLE validation_check (
    id UUID PRIMARY KEY,
    validation_run_id UUID NOT NULL,
    check_code VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    evidence_digest VARCHAR(128),
    details_json TEXT,
    CONSTRAINT fk_validation_check_run
        FOREIGN KEY (validation_run_id) REFERENCES validation_run (id)
);

CREATE INDEX ix_validation_run_tenant_target
    ON validation_run (tenant_id, target_digest);
