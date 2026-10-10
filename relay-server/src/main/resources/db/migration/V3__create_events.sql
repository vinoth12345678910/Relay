-- V3__create_events.sql
-- Events are published by apps and fanned out to matching endpoints as deliveries.

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    -- payload is TEXT, NOT jsonb: jsonb normalizes the document (reorders keys, drops
    -- duplicate keys, rewrites whitespace). The webhook HMAC signature is computed over the
    -- exact bytes we received, so we must store and send back that exact string.
    payload TEXT NOT NULL,
    idempotency_key TEXT,
    payload_hash TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- NULL keys never conflict in Postgres, so events without a key are unconstrained.
CREATE UNIQUE INDEX uq_events_app_idempotency ON events(app_id, idempotency_key);
CREATE INDEX idx_events_app_id_created_at ON events(app_id, created_at DESC);
