-- V5__create_delivery_attempts.sql
-- One row per HTTP attempt of a delivery: what happened, how long it took, what came back.

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ms BIGINT NOT NULL,
    status_code INTEGER,
    error TEXT,
    response_snippet TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    -- Also makes recording an attempt idempotent: a repeat insert is ignored, not duplicated.
    CONSTRAINT uq_attempt_delivery_number UNIQUE (delivery_id, attempt_number)
);

CREATE INDEX idx_delivery_attempts_delivery_id ON delivery_attempts(delivery_id);
