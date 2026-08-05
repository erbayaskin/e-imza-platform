ALTER TABLE signing_session ALTER COLUMN device_id DROP NOT NULL;
ALTER TABLE signing_session ADD COLUMN signing_mode VARCHAR(20) NOT NULL DEFAULT 'CLIENT_SIDE';
ALTER TABLE signing_session ADD COLUMN server_key_id VARCHAR(100);

ALTER TABLE signing_session ADD CONSTRAINT ck_signing_session_target
CHECK (
    (signing_mode = 'CLIENT_SIDE' AND device_id IS NOT NULL AND server_key_id IS NULL)
    OR
    (signing_mode = 'SERVER_SIDE' AND device_id IS NULL AND server_key_id IS NOT NULL)
);
