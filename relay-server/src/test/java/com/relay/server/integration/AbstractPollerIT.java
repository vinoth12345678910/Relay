// File: relay-server/src/test/java/com/relay/server/integration/AbstractPollerIT.java
package com.relay.server.integration;

import com.relay.server.worker.DeliveryPoller;
import com.relay.server.worker.SignatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Base for tests that run the real poller against a local receiver. The poller is stopped and the
 * shared database's leftover work retired before each test, and the context is discarded afterwards
 * so a running poller can never leak into other test classes.
 */
@Import(PollerTestConfig.class)
@TestPropertySource(properties = {"relay.worker.enabled=true", "relay.poll-interval-ms=100",
        "relay.per-endpoint-max-concurrency=2"})   // the poller test classes use a pool of 2; the cap may not exceed it
abstract class AbstractPollerIT extends AbstractIntegrationTest {

    @Autowired
    protected DeliveryPoller poller;

    protected final SignatureService signer = new SignatureService();
    protected TestReceiver receiver;

    @BeforeEach
    void startFreshPollerAndReceiver() {
        poller.stop();
        jdbc.update("update deliveries set status = 'SUCCEEDED' where status in ('PENDING', 'IN_FLIGHT')");
        receiver = new TestReceiver();
        poller.start();
    }

    @AfterEach
    void stopPollerAndReceiver() {
        poller.stop();
        receiver.close();
    }

    protected JsonNode newEndpoint(TestApp app) {
        return createEndpointJson(app, "*", receiver.url());
    }

    protected UUID publishEvent(TestApp app) {
        return UUID.fromString(json(publish(app, "order.paid", "{\"n\":" + System.nanoTime() + "}", null)).get("id").asString());
    }

    protected String deliveryStatus(UUID eventId) {
        return jdbc.queryForObject("select status from deliveries where event_id = ?", String.class, eventId);
    }

    protected UUID deliveryId(UUID eventId) {
        return jdbc.queryForObject("select id from deliveries where event_id = ?", UUID.class, eventId);
    }

    protected List<Map<String, Object>> attempts(UUID eventId) {
        return jdbc.queryForList("select attempt_number, status_code, error from delivery_attempts "
                + "where delivery_id = ? order by attempt_number", deliveryId(eventId));
    }

    /** The key bytes used to sign: the create response shows them as {@code whsec_<base64url>}. */
    protected byte[] secretBytes(JsonNode createdEndpoint) {
        return Base64.getUrlDecoder().decode(createdEndpoint.get("signingSecret").asString().substring("whsec_".length()));
    }

    protected void await(String what, long timeoutMs, BooleanSupplier condition) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Timed out after " + timeoutMs + " ms waiting for: " + what);
    }
}
