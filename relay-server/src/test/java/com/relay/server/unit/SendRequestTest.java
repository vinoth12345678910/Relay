// File: relay-server/src/test/java/com/relay/server/unit/SendRequestTest.java
package com.relay.server.unit;

import com.relay.server.worker.SendRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SendRequestTest {

    @Test
    void toStringShowsOnlyIdentifiersAttemptAndUrl() {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        byte[] secret = "TOP-SECRET-SIGNING-KEY".getBytes(StandardCharsets.UTF_8);
        SendRequest r = new SendRequest(deliveryId, eventId, 3, "https://203.0.113.10/hook",
                secret, "{\"card\":\"4111-PAYLOAD-VALUE\"}");

        String text = r.toString();

        assertThat(text).contains(deliveryId.toString(), eventId.toString(), "attemptNumber=3",
                "https://203.0.113.10/hook");
        assertThat(text).doesNotContain("TOP-SECRET", "4111-PAYLOAD-VALUE", "card", "signingSecret", "payload")
                .doesNotContain(Arrays.toString(secret))
                .doesNotContain(secret.toString());
    }
}
