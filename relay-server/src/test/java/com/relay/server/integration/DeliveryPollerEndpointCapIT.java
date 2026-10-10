// File: relay-server/src/test/java/com/relay/server/integration/DeliveryPollerEndpointCapIT.java
package com.relay.server.integration;

import com.relay.server.worker.EndpointConcurrencyLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** One slow receiver must not starve a fast one: the per-endpoint cap keeps workers free. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"relay.worker-pool-size=8", "relay.per-endpoint-max-concurrency=2"})
class DeliveryPollerEndpointCapIT extends AbstractPollerIT {

    @Autowired
    private EndpointConcurrencyLimiter limiter;

    private long succeeded(List<UUID> events) {
        return events.stream().filter(e -> "SUCCEEDED".equals(deliveryStatus(e))).count();
    }

    @Test
    void aSlowEndpointIsCappedAndAFastOneIsNotStarved() throws Exception {
        receiver.delayMs = 600;                       // endpoint A: slow
        try (TestReceiver fast = new TestReceiver()) {   // endpoint B: fast
            TestApp app = createApp();
            UUID endpointA = UUID.fromString(createEndpointJson(app, "slow.*", receiver.url()).get("id").asString());
            UUID endpointB = UUID.fromString(createEndpointJson(app, "fast.*", fast.url()).get("id").asString());
            List<UUID> slowEvents = new ArrayList<>();
            List<UUID> fastEvents = new ArrayList<>();
            for (int i = 0; i < 10; i++) {            // A's backlog is queued first, so it is claimed first
                slowEvents.add(UUID.fromString(json(publish(app, "slow.event", "{\"n\":" + i + "}", null)).get("id").asString()));
            }
            for (int i = 0; i < 5; i++) {
                fastEvents.add(UUID.fromString(json(publish(app, "fast.event", "{\"n\":" + i + "}", null)).get("id").asString()));
            }

            await("all fast deliveries done", 20_000, () -> succeeded(fastEvents) == 5);
            long slowDoneWhenFastFinished = succeeded(slowEvents);
            await("all slow deliveries done", 90_000, () -> succeeded(slowEvents) == 10);

            assertThat(slowDoneWhenFastFinished).as("the fast endpoint finished while the slow one was still draining")
                    .isLessThan(10);
            assertThat(receiver.maxInFlight()).as("never more than the cap on the slow endpoint").isEqualTo(2);
            assertThat(fast.total()).isEqualTo(5);
            assertThat(receiver.total()).as("each slow delivery sent exactly once").isEqualTo(10);
            // A delivery turned away at the door must not be charged an attempt.
            for (UUID e : slowEvents) {
                assertThat(attempts(e)).extracting(a -> a.get("attempt_number")).containsExactly(1);
            }
            for (UUID e : fastEvents) {
                assertThat(attempts(e)).extracting(a -> a.get("attempt_number")).containsExactly(1);
            }
            // No leaked door slots: both doors are fully free again.
            assertThat(limiter.tryEnter(endpointA)).isTrue();
            assertThat(limiter.tryEnter(endpointA)).isTrue();
            assertThat(limiter.tryEnter(endpointA)).isFalse();
            assertThat(limiter.tryEnter(endpointB)).isTrue();
        }
    }
}
