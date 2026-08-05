CREATE TABLE validation_policy_configuration (
    id UUID PRIMARY KEY,
    version_number BIGINT NOT NULL UNIQUE,
    mode VARCHAR(30) NOT NULL,
    qc_compliance_active BOOLEAN NOT NULL,
    certificate_policy_active BOOLEAN NOT NULL,
    revocation_active BOOLEAN NOT NULL,
    signature_policy_active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_validation_policy_configuration_version
    ON validation_policy_configuration (version_number DESC);
