CREATE TABLE client_device (
    device_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    algorithm VARCHAR(20) NOT NULL,
    public_key BYTEA NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_client_device_tenant ON client_device (tenant_id, enabled);
