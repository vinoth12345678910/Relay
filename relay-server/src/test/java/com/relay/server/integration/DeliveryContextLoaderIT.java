// File: relay-server/src/test/java/com/relay/server/integration/DeliveryContextLoaderIT.java
package com.relay.server.integration;

import com.relay.server.delivery.Delivery;
import com.relay.server.delivery.DeliveryRepository;
import com.relay.server.worker.ClaimedDelivery;
import com.relay.server.worker.DeliveryContextLoader;
import com.relay.server.worker.SendRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryContextLoaderIT extends AbstractIntegrationTest {

    @Autowired
    private DeliveryContextLoader loader;
    @Autowired
    private DeliveryRepository deliveryRepository;

    private record Setup(TestApp app, UUID endpointId, JsonNode endpoint, ClaimedDelivery claimed, UUID eventId) {}

    private Setup setup() {
        TestApp app = createApp();
        JsonNode endpoint = createEndpointJson(app, "*", "https://203.0.113.10/hooks");
        UUID eventId = UUID.fromString(json(publish(app, "order.paid", "{\"b\":2,  \"a\":1}", null)).get("id").asString());
        Delivery d = deliveryRepository.findByEventId(eventId).get(0);
        return new Setup(app, d.getEndpointId(), endpoint, new ClaimedDelivery(d.getId(), eventId, d.getEndpointId(), 1, 0), eventId);
    }

    @Test
    void loadsUrlExactPayloadAndTheDecryptedSecret() {
        Setup s = setup();

        SendRequest r = loader.load(s.claimed()).orElseThrow();

        assertThat(r.deliveryId()).isEqualTo(s.claimed().id());
        assertThat(r.eventId()).isEqualTo(s.eventId());
        assertThat(r.attemptNumber()).isEqualTo(1);
        assertThat(r.url()).isEqualTo("https://203.0.113.10/hooks");
        assertThat(r.payload()).isEqualTo(jdbc.queryForObject("select payload from events where id = ?", String.class, s.eventId()));
        byte[] shown = Base64.getUrlDecoder().decode(s.endpoint().get("signingSecret").asString().substring("whsec_".length()));
        assertThat(r.signingSecret()).isEqualTo(shown).hasSize(32);
    }

    @Test
    void pausedOrDeletedEndpointGivesEmpty() {
        Setup paused = setup();
        pauseEndpoint(paused.app(), paused.endpointId());
        Setup deleted = setup();
        send("DELETE", "/api/v1/endpoints/" + deleted.endpointId(), deleted.app().apiKey(), null, null);

        assertThat(loader.load(paused.claimed())).isEqualTo(Optional.empty());
        assertThat(loader.load(deleted.claimed())).isEqualTo(Optional.empty());
    }
}
