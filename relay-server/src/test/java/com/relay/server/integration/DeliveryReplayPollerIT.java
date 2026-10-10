// File: relay-server/src/test/java/com/relay/server/integration/DeliveryReplayPollerIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Replay end to end: the real poller gives a replayed delivery a fresh retry budget. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"relay.worker-pool-size=2", "relay.retry-schedule=1,1"})
class DeliveryReplayPollerIT extends AbstractPollerIT {

    @Test
    void replayedDeliveryGetsAFullNewBudgetAndAttemptNumbersKeepGrowing() {
        receiver.statusFor = n -> 500;
        TestApp app = createApp();
        newEndpoint(app);
        UUID eventId = publishEvent(app);
        await("first budget used up", 20_000, () -> "DEAD".equals(deliveryStatus(eventId)));
        assertThat(attempts(eventId)).extracting(a -> a.get("attempt_number")).containsExactly(1, 2, 3);
        UUID deliveryId = deliveryId(eventId);

        // Replay while the receiver still fails: a whole new budget of 3 attempts, numbered 4..6.
        assertThat(send("POST", "/api/v1/deliveries/" + deliveryId + "/replay", app.apiKey(), null, null).statusCode()).isEqualTo(202);
        await("second budget used up", 20_000, () -> attempts(eventId).size() == 6 && "DEAD".equals(deliveryStatus(eventId)));

        assertThat(attempts(eventId)).extracting(a -> a.get("attempt_number")).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(jdbc.queryForObject("select attempt_base from deliveries where id = ?", Integer.class, deliveryId)).isEqualTo(3);
        assertThat(receiver.total()).isEqualTo(6);
        assertThat(receiver.hits().get(5).headers().get("relay-attempt")).as("header stays absolute").isEqualTo("6");

        // Fix the receiver and replay again: delivered on the first try of the third budget.
        receiver.statusFor = n -> 200;
        assertThat(send("POST", "/api/v1/deliveries/" + deliveryId + "/replay", app.apiKey(), null, null).statusCode()).isEqualTo(202);
        await("delivered after replay", 10_000, () -> "SUCCEEDED".equals(deliveryStatus(eventId)));

        assertThat(attempts(eventId)).hasSize(7);
        assertThat(attempts(eventId).get(6).get("attempt_number")).isEqualTo(7);
        assertThat(attempts(eventId).get(6).get("status_code")).isEqualTo(200);
        assertThat(jdbc.queryForObject("select attempt_base from deliveries where id = ?", Integer.class, deliveryId)).isEqualTo(6);
    }
}
