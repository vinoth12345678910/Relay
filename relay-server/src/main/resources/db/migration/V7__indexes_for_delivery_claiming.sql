-- The claim query only ever reads two kinds of rows:
--   PENDING   rows whose next_attempt_at is due
--   IN_FLIGHT rows whose lease (locked_until) has expired
-- SUCCEEDED and DEAD rows are the vast majority over time and are never claimed.
-- A PARTIAL index covers only the rows we query, so it stays small and fast
-- regardless of how many finished deliveries pile up. A full index would grow
-- forever with rows nobody searches for.

CREATE INDEX idx_deliveries_claim_pending
    ON deliveries (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_deliveries_claim_expired_lease
    ON deliveries (locked_until)
    WHERE status = 'IN_FLIGHT';

-- Powers the listing API: "my deliveries for this endpoint, newest first".
-- id is the tiebreaker so cursor pagination never skips or repeats rows.
CREATE INDEX idx_deliveries_endpoint_listing
    ON deliveries (endpoint_id, created_at DESC, id DESC);