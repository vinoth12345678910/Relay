-- attempt_count only ever grows, so delivery_attempts(delivery_id, attempt_number) stays unique.
-- attempt_base marks where the current retry budget started (set by replay).
-- Relative attempt = attempt_count - attempt_base. Backoff and the max-attempts check use the
-- relative number; the Relay-Attempt header and attempt rows keep the absolute one.
ALTER TABLE deliveries ADD COLUMN attempt_base INTEGER NOT NULL DEFAULT 0;