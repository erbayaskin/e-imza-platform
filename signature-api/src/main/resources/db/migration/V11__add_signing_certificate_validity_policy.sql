ALTER TABLE validation_policy_configuration
    ADD COLUMN signing_certificate_validity_active BOOLEAN NOT NULL DEFAULT TRUE;
