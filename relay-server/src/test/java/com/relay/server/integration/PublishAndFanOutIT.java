// File: relay-server/src/test/java/com/relay/server/integration/PublishAndFanOutIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublishAndFanOutIT extends AbstractIntegrationTest {

    @Test
    void publishFansOutToMatchingActiveAndPausedEndpointsButNotDeletedOnes() {
        TestApp app = createApp();
        UUID exact = createEndpoint(app, "order.paid");
        UUID prefix = createEndpoint(app, "order.*");
        createEndpoint(app, "user.created");
        UUID paused = createEndpoint(app, "*");
        pauseEndpoint(app, paused);                       // paused endpoints still queue deliveries
        UUID deleted = createEndpoint(app, "*");
        send("DELETE", "/api/v1/endpoints/" + deleted, app.apiKey(), null, null);

        HttpResponse<String> response = publish(app, "order.paid", "{\"amount\":42}", null);

        assertThat(response.statusCode()).isEqualTo(202);
        JsonNode body = json(response);
        assertThat(body.get("deliveryCount").asLong()).isEqualTo(3);
        assertThat(body.get("replayed").asBoolean()).isFalse();

        JsonNode event = json(get("/api/v1/events/" + body.get("id").asString(), app.apiKey()));
        List<String> endpointIds = event.get("deliveries").valueStream()
                .map(d -> d.get("endpointId").asString()).toList();
        assertThat(endpointIds).containsExactlyInAnyOrder(exact.toString(), prefix.toString(), paused.toString());
        event.get("deliveries").forEach(d -> {
            assertThat(d.get("status").asString()).isEqualTo("PENDING");
            assertThat(d.get("attemptCount").asInt()).isZero();
        });
        assertThat(event.get("payload").get("amount").asInt()).isEqualTo(42);
    }

    @Test
    void eventMatchingNothingIsStoredWithZeroDeliveries() {
        TestApp app = createApp();
        createEndpoint(app, "user.created");

        HttpResponse<String> response = publish(app, "invoice.sent", "{\"n\":1}", null);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(json(response).get("deliveryCount").asLong()).isZero();
        assertThat(countEvents(app.id())).isEqualTo(1);
        assertThat(countDeliveries(app.id())).isZero();
    }
}
