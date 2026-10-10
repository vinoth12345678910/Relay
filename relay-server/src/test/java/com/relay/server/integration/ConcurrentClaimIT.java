// File: relay-server/src/test/java/com/relay/server/integration/ConcurrentClaimIT.java
package com.relay.server.integration;

import com.relay.server.worker.ClaimedDelivery;
import com.relay.server.worker.DeliveryClaimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Hammers the claim query: 500 deliveries, 8 competing workers, 10 rounds of fresh data. */
class ConcurrentClaimIT extends AbstractIntegrationTest {

    private static final int DELIVERIES = 500;
    private static final int WORKERS = 8;

    @Autowired
    private DeliveryClaimer claimer;

    /** The claimer sees every app's rows, so retire leftovers from other tests and earlier rounds. */
    @BeforeEach
    void retireOtherDeliveries() {
        jdbc.update("update deliveries set status = 'SUCCEEDED'");
    }

    @RepeatedTest(10)
    void everyDeliveryIsClaimedExactlyOnceAcrossWorkers() throws Exception {
        TestApp app = createApp();
        UUID endpointId = createEndpoint(app, "*");
        Set<UUID> expected = seed(app.id(), endpointId);

        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<UUID>>> futures = new ArrayList<>();
        for (int w = 0; w < WORKERS; w++) {
            futures.add(pool.submit((Callable<List<UUID>>) () -> {
                start.await();
                List<UUID> mine = new ArrayList<>();
                for (List<ClaimedDelivery> b = claimer.claimBatch(); !b.isEmpty(); b = claimer.claimBatch()) {
                    b.forEach(c -> mine.add(c.id()));
                }
                return mine;
            }));
        }
        start.countDown();
        List<UUID> claimed = new ArrayList<>();
        for (Future<List<UUID>> f : futures) {
            claimed.addAll(f.get());
        }
        pool.shutdown();

        assertThat(claimed).as("no duplicates across workers").hasSize(DELIVERIES);
        assertThat(new HashSet<>(claimed)).as("none missed, none foreign").isEqualTo(expected);
        assertThat(jdbc.queryForObject(
                "select count(*) from deliveries d join events e on e.id = d.event_id "
                        + "where e.app_id = ? and d.status = 'IN_FLIGHT' and d.attempt_count = 1",
                Long.class, app.id())).isEqualTo(DELIVERIES);
    }

    /** Bulk-inserts events plus one PENDING delivery each, and returns the delivery ids. */
    private Set<UUID> seed(UUID appId, UUID endpointId) {
        jdbc.update("insert into events(app_id, type, payload, payload_hash, created_at) "
                + "select ?, 'bulk.event', '{}', md5(g::text), now() from generate_series(1, ?) g", appId, DELIVERIES);
        jdbc.update("insert into deliveries(event_id, endpoint_id, next_attempt_at) "
                + "select id, ?, now() - interval '1 second' from events where app_id = ?", endpointId, appId);
        return new HashSet<>(jdbc.queryForList(
                "select d.id from deliveries d join events e on e.id = d.event_id where e.app_id = ?",
                UUID.class, appId));
    }
}
