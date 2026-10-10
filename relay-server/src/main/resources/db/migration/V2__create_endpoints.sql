-- V2__create_endpoints.sql
-- Endpoints are webhook destinations registered by apps.
-- Each endpoint has a URL, filter, signing secret, and status.

CREATE TABLE endpoints (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
                           url TEXT NOT NULL,
                           description TEXT,
                           event_type_filter TEXT NOT NULL DEFAULT '*',
                           signing_secret BYTEA NOT NULL,
                           status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'DISABLED')),
                           consecutive_failures INTEGER NOT NULL DEFAULT 0,
                           paused_reason TEXT,
                           created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_endpoints_app_id ON endpoints(app_id);
CREATE INDEX idx_endpoints_app_id_status ON endpoints(app_id, status);