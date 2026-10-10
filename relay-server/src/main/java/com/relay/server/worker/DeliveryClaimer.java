package com.relay.server.worker;

import com.relay.server.config.RelayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryClaimer {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final RelayProperties props;

    // NOTE: this query sets IN_FLIGHT directly instead of going through DeliveryStateMachine.
    // Its WHERE clause is what enforces the legal moves: only PENDING (due) or IN_FLIGHT
    // (lease expired) rows can be claimed, which are the PENDING->IN_FLIGHT and
    // IN_FLIGHT->IN_FLIGHT transitions. Every other status change goes through the state machine.
    //
    // THE claim query. It lives here and nowhere else.
    //  - SKIP LOCKED: two workers never wait on or double-claim the same row.
    //  - expired IN_FLIGHT rows are re-claimable: that IS crash recovery.
    //  - attempt_count is bumped HERE so a poison delivery can't loop forever.
    //  - EXISTS on endpoints: paused endpoints keep their backlog, no attempts.
    private static final String CLAIM_SQL = """
        UPDATE deliveries
        SET status = 'IN_FLIGHT',
            attempt_count = attempt_count + 1,
            locked_until = :lockedUntil,
            updated_at = :now
        WHERE id IN (
            SELECT d.id
            FROM deliveries d
            WHERE (
                    (d.status = 'PENDING'   AND d.next_attempt_at <= :now)
                 OR (d.status = 'IN_FLIGHT' AND d.locked_until   <  :now)
                  )
              AND EXISTS (SELECT 1 FROM endpoints e
                          WHERE e.id = d.endpoint_id AND e.status = 'ACTIVE')
            ORDER BY d.next_attempt_at
            LIMIT :batch
            FOR UPDATE OF d SKIP LOCKED
        )
        RETURNING id, event_id, endpoint_id, attempt_count, attempt_base
        """;

    /** Claims up to the configured batch size. */
    @Transactional
    public List<ClaimedDelivery> claimBatch() {
        return claimBatch(props.getBatchSize());
    }

    /** Short transaction: ONLY database work, never an HTTP call. Claims at most {@code limit}. */
    @Transactional
    public List<ClaimedDelivery> claimBatch(int limit) {
        Instant now = Instant.now(clock);
        Instant lockedUntil = now.plusSeconds(props.getLeaseSeconds());

        return jdbc.sql(CLAIM_SQL)
                .param("now", utc(now))
                .param("lockedUntil", utc(lockedUntil))
                .param("batch", limit)
                .query((rs, i) -> new ClaimedDelivery(
                        rs.getObject("id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("endpoint_id", UUID.class),
                        rs.getInt("attempt_count"),
                        rs.getInt("attempt_base")))
                .list();
    }

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}