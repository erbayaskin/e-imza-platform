ALTER TABLE signing_session
    ADD COLUMN signature_packaging VARCHAR(20) NOT NULL DEFAULT 'DETACHED';

ALTER TABLE signing_session
    ADD COLUMN requested_signature_algorithm VARCHAR(40);

UPDATE signing_session
SET signature_packaging = 'ENVELOPED'
WHERE format = 'PADES';
