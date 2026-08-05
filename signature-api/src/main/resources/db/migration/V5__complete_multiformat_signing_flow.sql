ALTER TABLE signing_session ADD COLUMN document_name VARCHAR(255) NOT NULL DEFAULT 'document';
ALTER TABLE signing_session ADD COLUMN media_type VARCHAR(100) NOT NULL DEFAULT 'application/octet-stream';
ALTER TABLE signing_session ADD COLUMN purpose VARCHAR(250) NOT NULL DEFAULT 'signing';
ALTER TABLE signing_session ADD COLUMN document_content BYTEA;

CREATE TABLE signing_preparation (
    session_id UUID PRIMARY KEY,
    preparation_type VARCHAR(16) NOT NULL,
    preparation_json TEXT NOT NULL,
    signing_certificate_fingerprint VARCHAR(64) NOT NULL,
    signing_certificate BYTEA NOT NULL,
    signature_algorithm VARCHAR(40) NOT NULL,
    reader_id VARCHAR(300) NOT NULL,
    manifest_nonce VARCHAR(100) NOT NULL UNIQUE,
    manifest_json TEXT NOT NULL,
    manifest_signature VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_signing_preparation_session
        FOREIGN KEY (session_id) REFERENCES signing_session (id)
);

ALTER TABLE signature_artifact ADD COLUMN encoded_artifact BYTEA;
ALTER TABLE signature_artifact ADD COLUMN media_type VARCHAR(100);
