// File: relay-server/src/test/java/com/relay/server/integration/CircuitBreakerIT.java
package com.relay.server.integration;

import com.relay.server.config.RelayProperties;
import com.relay.server.worker.CircuitBreakerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerIT extends AbstractIntegrationTest {

    @Autowired
    private CircuitBreakerService breaker;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private RelayProperties props;

    private record Row(String status, int failures, String reason) {}

    private int threshold() {
        return props.getBreakerThreshold();
    }

    private UUID newEndpoint() {
        return createEndpoint(createApp(), "*");
    }

    private void fail(UUID endpointId, int times) {
        for (int i = 0; i < times; i++) {
            tx.executeWithoutResult(s -> breaker.recordFailure(endpointId));
        }
    }

    private Row row(UUID endpointId) {
        return jdbc.queryForObject("select status, consecutive_failures, paused_reason from endpoints where id = ?",
                (rs, i) -> new Row(rs.getString(1), rs.getInt(2), rs.getString(3)), endpointId);
    }

    @Test
    void belowTheThresholdStaysActive() {
        UUID ep = newEndpoint();

        fail(ep, threshold() - 1);

        assertThat(row(ep)).isEqualTo(new Row("ACTIVE", threshold() - 1, null));
    }

    @Test
    void reachingTheThresholdPausesWithAReason() {
        UUID ep = newEndpoint();

        fail(ep, threshold());

        Row r = row(ep);
        assertThat(r.status()).isEqualTo("PAUSED");
        assertThat(r.failures()).isEqualTo(threshold());
        assertThat(r.reason()).isEqualTo("circuit breaker: " + threshold() + " consecutive failures");
    }

    @Test
    void aSuccessEndsTheStreak() {
        UUID ep = newEndpoint();
        fail(ep, threshold() - 1);

        tx.executeWithoutResult(s -> breaker.recordSuccess(ep));
        assertThat(row(ep).failures()).isZero();
        fail(ep, threshold() - 1);   // the streak restarted, so this still does not trip

        assertThat(row(ep).status()).isEqualTo("ACTIVE");
    }

    @Test
    void pauseNowSetsTheReasonAndLaterFailuresNeverOverwriteAnyPause() {
        UUID ep = newEndpoint();

        tx.executeWithoutResult(s -> breaker.pauseNow(ep, "receiver returned 410 Gone"));
        fail(ep, threshold());

        assertThat(row(ep).status()).isEqualTo("PAUSED");
        assertThat(row(ep).reason()).isEqualTo("receiver returned 410 Gone");
    }

    @Test
    void aManualPauseKeepsItsReasonToo() {
        TestApp app = createApp();
        UUID ep = createEndpoint(app, "*");
        send("POST", "/api/v1/endpoints/" + ep + "/pause", app.apiKey(), "{\"reason\":\"maintenance\"}", null);

        fail(ep, threshold());

        assertThat(row(ep).reason()).isEqualTo("maintenance");
    }

    @Test
    void aDeletedEndpointIsNeverResurrectedByLateFailures() {
        TestApp app = createApp();
        UUID ep = createEndpoint(app, "*");
        send("DELETE", "/api/v1/endpoints/" + ep, app.apiKey(), null, null);   // soft delete: DISABLED

        fail(ep, threshold());   // in-flight deliveries finishing after the delete

        assertThat(row(ep).status()).isEqualTo("DISABLED");
        assertThat(row(ep).reason()).isNull();
        assertThat(get("/api/v1/endpoints/" + ep, app.apiKey()).statusCode()).isEqualTo(404);
    }

    @Test
    void resumeStartsACleanStreak() {
        TestApp app = createApp();
        UUID ep = createEndpoint(app, "*");
        fail(ep, threshold());
        assertThat(row(ep).status()).isEqualTo("PAUSED");

        send("POST", "/api/v1/endpoints/" + ep + "/resume", app.apiKey(), null, null);
        fail(ep, 1);

        assertThat(row(ep)).isEqualTo(new Row("ACTIVE", 1, null));   // not tripped again by an old count
    }

    @Test
    void concurrentFailuresLoseNoIncrements() throws Exception {
        UUID ep = newEndpoint();
        int n = threshold() - 1;   // stay below the threshold: we are counting, not tripping
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                tx.executeWithoutResult(s -> breaker.recordFailure(ep));
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(row(ep).failures()).isEqualTo(n);
    }

    @Test
    void mustRunInsideTheCallersTransaction() {
        UUID ep = newEndpoint();

        assertThatThrownBy(() -> breaker.recordFailure(ep)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> breaker.recordSuccess(ep)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> breaker.pauseNow(ep, "x")).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(row(ep).failures()).isZero();
    }
}
