// File: relay-server/src/test/java/com/relay/server/integration/CircuitBreakerPollerIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The breaker end to end with the real poller: a dead receiver is cut off, its backlog waits, resume drains it. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"relay.worker-pool-size=2", "relay.breaker-threshold=3",
        "relay.retry-schedule=1,1,1,1,1,1"})
class CircuitBreakerPollerIT extends AbstractPollerIT {

    private String endpointStatus(UUID endpointId) {
        return jdbc.queryForObject("select status from endpoints where id = ?", String.class, endpointId);
    }

    @Test
    void deadReceiverTripsTheBreakerBacklogWaitsAndResumeDrainsIt() throws Exception {
        receiver.statusFor = n -> 500;
        TestApp app = createApp();
        UUID endpointId = UUID.fromString(newEndpoint(app).get("id").asString());
        List<UUID> events = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            events.add(publishEvent(app));
        }

        await("breaker opens", 20_000, () -> "PAUSED".equals(endpointStatus(endpointId)));
        int hitsWhenPaused = receiver.total();
        Thread.sleep(2500);   // plenty of poll cycles and retry delays

        assertThat(hitsWhenPaused).as("3 failures trip it; at most pool-size - 1 more were already in flight").isBetween(3, 4);
        assertThat(receiver.total()).as("nothing sent after the pause").isEqualTo(hitsWhenPaused);
        assertThat(jdbc.queryForObject("select paused_reason from endpoints where id = ?", String.class, endpointId))
                .isEqualTo("circuit breaker: 3 consecutive failures");
        for (UUID e : events) {
            assertThat(deliveryStatus(e)).as("backlog is kept, not dead").isEqualTo("PENDING");
        }

        // An event published while the endpoint is paused is queued too.
        UUID late = publishEvent(app);
        assertThat(deliveryStatus(late)).isEqualTo("PENDING");

        receiver.statusFor = n -> 200;
        send("POST", "/api/v1/endpoints/" + endpointId + "/resume", app.apiKey(), null, null);
        await("backlog drained", 30_000, () -> events.stream().allMatch(e -> "SUCCEEDED".equals(deliveryStatus(e)))
                && "SUCCEEDED".equals(deliveryStatus(late)));

        assertThat(endpointStatus(endpointId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select consecutive_failures from endpoints where id = ?", Integer.class, endpointId)).isZero();
    }

    @Test
    void goneResponsePausesTheEndpointAtOnce() {
        receiver.statusFor = n -> 410;
        TestApp app = createApp();
        JsonNode endpoint = newEndpoint(app);
        UUID endpointId = UUID.fromString(endpoint.get("id").asString());
        UUID eventId = publishEvent(app);

        await("endpoint paused by 410", 10_000, () -> "PAUSED".equals(endpointStatus(endpointId)));

        assertThat(jdbc.queryForObject("select paused_reason from endpoints where id = ?", String.class, endpointId))
                .isEqualTo("receiver returned 410 Gone");
        assertThat(receiver.total()).isEqualTo(1);
        assertThat(deliveryStatus(eventId)).isEqualTo("PENDING");   // retry is scheduled but blocked by the pause
    }
}
