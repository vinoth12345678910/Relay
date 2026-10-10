// File: relay-server/src/test/java/com/relay/server/integration/TenantIsolationIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationIT extends AbstractIntegrationTest {

    @Test
    void anotherAppsEventIsNotFound() {
        TestApp appA = createApp();
        TestApp appB = createApp();
        String eventId = json(publish(appA, "order.paid", "{\"a\":1}", null)).get("id").asString();

        HttpResponse<String> asOwner = get("/api/v1/events/" + eventId, appA.apiKey());
        HttpResponse<String> asOther = get("/api/v1/events/" + eventId, appB.apiKey());

        assertThat(asOwner.statusCode()).isEqualTo(200);
        assertThat(asOther.statusCode()).isEqualTo(404);
        assertThat(json(asOther).get("code").asString()).isEqualTo("NOT_FOUND");
    }
}
