// File: relay-server/src/test/java/com/relay/server/integration/DeliveryPollerDeadIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"relay.worker-pool-size=2", "relay.retry-schedule=1,1"})
class DeliveryPollerDeadIT extends AbstractPollerIT {

    @Test
    void alwaysFailingReceiverEndsDeadAfterThreeAttempts() {
        receiver.statusFor = n -> 500;
        TestApp app = createApp();
        newEndpoint(app);
        UUID eventId = publishEvent(app);

        await("delivery DEAD", 20_000, () -> "DEAD".equals(deliveryStatus(eventId)));

        assertThat(attempts(eventId)).extracting(a -> a.get("attempt_number")).containsExactly(1, 2, 3);
        assertThat(attempts(eventId)).extracting(a -> a.get("status_code")).containsExactly(500, 500, 500);
        assertThat(receiver.total()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select attempt_count from deliveries where event_id = ?", Integer.class, eventId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select locked_until is null from deliveries where event_id = ?", Boolean.class, eventId))
                .isTrue();
    }
}
