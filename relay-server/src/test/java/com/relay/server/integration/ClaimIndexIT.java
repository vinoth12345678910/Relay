// File: relay-server/src/test/java/com/relay/server/integration/ClaimIndexIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the claim query's performance: with a table that is almost all finished work, the planner
 * must use the partial claim indexes (V7) instead of scanning every delivery.
 */
class ClaimIndexIT extends AbstractIntegrationTest {

    private static final int EVENTS = 20_000;
    private static final int PENDING = 100;

    private UUID seededAppId;

    /** The seed is large; remove it so other tests see a small table. Deleting events cascades to deliveries. */
    @AfterEach
    void removeSeededData() {
        if (seededAppId != null) {
            jdbc.update("delete from events where app_id = ?", seededAppId);
        }
    }

    @Test
    void claimQueryUsesPartialIndexNotSeqScan() {
        TestApp app = createApp();
        UUID endpointId = createEndpoint(app, "*");
        seededAppId = app.id();

        // 20,000 events with one delivery each: 100 due PENDING, the rest SUCCEEDED (never claimed).
        jdbc.update("insert into events (app_id, type, payload, payload_hash, created_at) "
                + "select ?, 'order.paid', '{}', 'h', now() from generate_series(1, ?)", app.id(), EVENTS);
        jdbc.update("insert into deliveries (event_id, endpoint_id, status, attempt_count, next_attempt_at, created_at, updated_at) "
                + "select id, ?, case when row_number() over () <= ? then 'PENDING' else 'SUCCEEDED' end, "
                + "0, now() - interval '1 minute', now(), now() from events where app_id = ?", endpointId, PENDING, app.id());
        assertThat(jdbc.queryForObject("select count(*) from deliveries d join events e on e.id = d.event_id "
                + "where e.app_id = ? and d.status = 'PENDING'", Long.class, app.id())).isEqualTo(PENDING);

        jdbc.execute("ANALYZE deliveries");   // real statistics, so the planner can see the table is mostly SUCCEEDED

        // The claim's inner SELECT. EXPLAIN cannot show FOR UPDATE SKIP LOCKED cleanly, and the plan is the same.
        List<String> plan = jdbc.queryForList("""
                EXPLAIN
                SELECT d.id FROM deliveries d
                WHERE ((d.status = 'PENDING' AND d.next_attempt_at <= now())
                    OR (d.status = 'IN_FLIGHT' AND d.locked_until < now()))
                ORDER BY d.next_attempt_at
                LIMIT 10
                """, String.class);

        String joined = String.join("\n", plan);
        // The query ORs two cases (due PENDING, expired IN_FLIGHT), so a seq-scan-free plan needs both partial indexes.
        assertThat(joined).as("plan:%n%s", joined).contains("idx_deliveries_claim_pending", "idx_deliveries_claim_expired_lease");
        assertThat(joined).as("plan:%n%s", joined).doesNotContain("Seq Scan on deliveries");
    }
}
