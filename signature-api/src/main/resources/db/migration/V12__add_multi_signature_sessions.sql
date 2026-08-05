ALTER TABLE signing_session
    ADD COLUMN multi_signature_type VARCHAR(16) NOT NULL DEFAULT 'SINGLE';

ALTER TABLE signing_session
    ADD COLUMN existing_artifact BYTEA;

ALTER TABLE signing_session
    ADD COLUMN target_signature_index INTEGER NOT NULL DEFAULT 0;
