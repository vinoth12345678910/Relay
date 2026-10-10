// File: relay-server/src/test/java/com/relay/server/integration/ConcurrentPublishIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentPublishIT extends AbstractIntegrationTest {

    private static final int THREADS = 20;

    // Smoke test of real concurrency; IdempotencyRaceIT is what deterministically proves the unique-violation path.
    @Test
    void concurrentPublishesWithSameKeyCreateExactlyOneEvent() throws Exception {
        TestApp app = createApp();
        createEndpoint(app, "*");
        createEndpoint(app, "order.*");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return publish(app, "order.paid", "{\"a\":1}", "race-key");
            }));
        }
        start.countDown();

        List<HttpResponse<String>> responses = new ArrayList<>();
        for (Future<HttpResponse<String>> f : futures) {
            responses.add(f.get());
        }
        pool.shutdown();

        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(202);
        assertThat(responses.stream().map(r -> json(r).get("id").asString()).distinct()).hasSize(1);
        assertThat(responses.stream().filter(r -> !json(r).get("replayed").asBoolean()).count()).isEqualTo(1);
        assertThat(responses.stream().map(r -> json(r).get("deliveryCount").asLong()).distinct()).containsExactly(2L);
        assertThat(countEvents(app.id())).isEqualTo(1);
        assertThat(countDeliveries(app.id())).isEqualTo(2);
    }
}
