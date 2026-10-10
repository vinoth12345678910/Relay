// File: relay-server/src/test/java/com/relay/server/integration/EventValidationIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class EventValidationIT extends AbstractIntegrationTest {

    @Test
    void badTypeIsBadRequest() {
        TestApp app = createApp();

        HttpResponse<String> r = publish(app, "Order Paid!", "{\"a\":1}", null);

        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(json(r).get("fieldErrors").get(0).get("field").asString()).isEqualTo("type");
    }

    @Test
    void missingTypeIsBadRequest() {
        TestApp app = createApp();

        HttpResponse<String> r = send("POST", "/api/v1/events", app.apiKey(), "{\"payload\":{\"a\":1}}", null);

        assertThat(r.statusCode()).isEqualTo(400);
    }

    @Test
    void nullPayloadIsBadRequest() {
        TestApp app = createApp();

        HttpResponse<String> r = publish(app, "order.paid", "null", null);

        assertThat(r.statusCode()).isEqualTo(400);
    }

    @Test
    void payloadOver256KbIsTooLarge() {
        TestApp app = createApp();
        String big = "{\"blob\":\"" + "x".repeat(257 * 1024) + "\"}";

        HttpResponse<String> r = publish(app, "order.paid", big, null);

        assertThat(r.statusCode()).isEqualTo(413);
        assertThat(json(r).get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(countEvents(app.id())).isZero();
    }
}
