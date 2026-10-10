-- V1__create_apps.sql
-- Apps are tenants. Each app has an API key and owns endpoints and events.

CREATE TABLE apps (
                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                      name TEXT NOT NULL,
                      api_key_hash TEXT NOT NULL UNIQUE,
                      api_key_prefix TEXT NOT NULL,
                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_apps_api_key_hash ON apps(api_key_hash);