// File: relay-server/src/main/java/com/relay/server/worker/CircuitBreakerService.java
package com.relay.server.worker;

import com.relay.server.config.RelayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Tracks consecutive failures per endpoint and pauses it at the threshold.
 *
 * <p>Every method must run INSIDE the result handler's transaction ({@code MANDATORY}), so the
 * delivery outcome and the breaker update commit together or not at all. Waiting deliveries are kept
 * (fan-out still queues for PAUSED endpoints) and the claim SQL skips paused endpoints, so nothing is
 * lost: resuming the endpoint drains the backlog, throttled by the worker pool size.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final RelayProperties props;

    private record Outcome(String status, int failures, String reason) {}

    /** A success ends the streak. The WHERE skips the write when it is already 0 (the common case). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(UUID endpointId) {
        jdbc.sql("""
                        UPDATE endpoints SET consecutive_failures = 0, updated_at = :now
                        WHERE id = :id AND consecutive_failures > 0
                        """)
                .param("now", now())
                .param("id", endpointId)
                .update();
    }

    /**
     * One atomic statement: increment, and pause if this failure reaches the threshold. Done in SQL
     * rather than read-then-write in Java so many workers failing at once cannot lose an increment.
     * SET expressions see the OLD row, hence {@code consecutive_failures + 1} in the CASE. Only an
     * ACTIVE endpoint is paused, so a manual pause or a 410 reason is never overwritten.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFailure(UUID endpointId) {
        int threshold = props.getBreakerThreshold();
        String reason = "circuit breaker: " + threshold + " consecutive failures";
        jdbc.sql("""
                        UPDATE endpoints
                        SET consecutive_failures = consecutive_failures + 1,
                            status = CASE WHEN status = 'ACTIVE' AND consecutive_failures + 1 >= :threshold
                                          THEN 'PAUSED' ELSE status END,
                            paused_reason = CASE WHEN status = 'ACTIVE' AND consecutive_failures + 1 >= :threshold
                                                 THEN :reason ELSE paused_reason END,
                            updated_at = :now
                        WHERE id = :id
                        RETURNING status, consecutive_failures, paused_reason
                        """)
                .param("threshold", threshold)
                .param("reason", reason)
                .param("now", now())
                .param("id", endpointId)
                .query((rs, i) -> new Outcome(rs.getString("status"), rs.getInt("consecutive_failures"), rs.getString("paused_reason")))
                .optional()
                .filter(o -> "PAUSED".equals(o.status()) && o.failures() == threshold && reason.equals(o.reason()))
                .ifPresent(o -> log.warn("Circuit breaker opened: endpointId={} after {} consecutive failures", endpointId, threshold));
    }

    /** Immediate pause (for example a 410 Gone). No-op unless the endpoint is ACTIVE. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void pauseNow(UUID endpointId, String reason) {
        int updated = jdbc.sql("""
                        UPDATE endpoints SET status = 'PAUSED', paused_reason = :reason, updated_at = :now
                        WHERE id = :id AND status = 'ACTIVE'
                        """)
                .param("reason", reason)
                .param("now", now())
                .param("id", endpointId)
                .update();
        if (updated > 0) {
            log.warn("Endpoint paused: endpointId={} reason={}", endpointId, reason);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
