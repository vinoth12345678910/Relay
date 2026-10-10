// File: relay-server/src/test/java/com/relay/server/integration/DeliveryClaimerIT.java
package com.relay.server.integration;

import com.relay.server.worker.ClaimedDelivery;
import com.relay.server.worker.DeliveryClaimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the claim query against real Postgres: lease, crash recovery, paused endpoints, SKIP LOCKED. */
class DeliveryClaimerIT extends AbstractIntegrationTest {

    @Autowired
    private DeliveryClaimer claimer;

    /** The claimer sees every app's rows, so retire leftovers from other tests first. */
    @BeforeEach
    void retireOtherDeliveries() {
        jdbc.update("update deliveries set status = 'SUCCEEDED'");
    }

    @Test
    void claimsPendingOnceAndHoldsTheLease() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        publish(app, "order.paid", "{\"a\":1}", null);

        List<ClaimedDelivery> first = claimer.claimBatch();
        List<ClaimedDelivery> second = claimer.claimBatch();

        assertThat(first).hasSize(1);
        assertThat(first.get(0).attemptNumber()).isEqualTo(1);
        assertThat(second).isEmpty();
        assertThat(jdbc.queryForObject("select status from deliveries where id = ?", String.class, first.get(0).id()))
                .isEqualTo("IN_FLIGHT");
    }

    @Test
    void expiredLeaseIsReclaimedAsCrashRecovery() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        publish(app, "order.paid", "{\"a\":1}", null);
        ClaimedDelivery crashed = claimer.claimBatch().get(0);
        jdbc.update("update deliveries set locked_until = now() - interval '1 minute' where id = ?", crashed.id());

        List<ClaimedDelivery> recovered = claimer.claimBatch();

        assertThat(recovered).extracting(ClaimedDelivery::id).containsExactly(crashed.id());
        assertThat(recovered.get(0).attemptNumber()).isEqualTo(2);
    }

    @Test
    void pausedEndpointKeepsItsBacklogUntilResumed() {
        TestApp app = createApp();
        UUID endpoint = createEndpoint(app, "*");
        publish(app, "order.paid", "{\"a\":1}", null);
        pauseEndpoint(app, endpoint);

        assertThat(claimer.claimBatch()).isEmpty();
        send("POST", "/api/v1/endpoints/" + endpoint + "/resume", app.apiKey(), null, null);

        assertThat(claimer.claimBatch()).hasSize(1);
    }

    @Test
    void futureAttemptsAreNotClaimedYet() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        publish(app, "order.paid", "{\"a\":1}", null);
        jdbc.update("update deliveries set next_attempt_at = now() + interval '1 hour' where status = 'PENDING'");

        assertThat(claimer.claimBatch()).isEmpty();
    }

    @Test
    void concurrentClaimersNeverShareADelivery() throws Exception {
        TestApp app = createApp();
        createEndpoint(app, "*");
        int total = 40;
        for (int i = 0; i < total; i++) {
            publish(app, "order.paid", "{\"n\":" + i + "}", null);
        }

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<List<UUID>>> futures = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            futures.add(pool.submit((Callable<List<UUID>>) () -> {
                List<UUID> mine = new ArrayList<>();
                for (List<ClaimedDelivery> b = claimer.claimBatch(); !b.isEmpty(); b = claimer.claimBatch()) {
                    b.forEach(c -> mine.add(c.id()));
                }
                return mine;
            }));
        }
        List<UUID> all = new ArrayList<>();
        for (Future<List<UUID>> f : futures) {
            all.addAll(f.get());
        }
        pool.shutdown();

        Set<UUID> distinct = new HashSet<>(all);
        assertThat(all).hasSize(total);
        assertThat(distinct).hasSize(total);
    }
}
