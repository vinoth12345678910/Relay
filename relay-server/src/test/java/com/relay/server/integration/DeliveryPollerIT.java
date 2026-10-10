// File: relay-server/src/test/java/com/relay/server/integration/DeliveryPollerIT.java
package com.relay.server.integration;

import com.relay.server.config.RelayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"relay.worker-pool-size=2", "relay.retry-schedule=1,1,1"})
class DeliveryPollerIT extends AbstractPollerIT {

    @Autowired
    private RelayProperties props;

    @Test
    void deliversEndToEndWithVerifiableSignature() {
        TestApp app = createApp();
        JsonNode endpoint = newEndpoint(app);
        UUID eventId = publishEvent(app);

        await("delivery SUCCEEDED", 10_000, () -> "SUCCEEDED".equals(deliveryStatus(eventId)));

        assertThat(attempts(eventId)).singleElement().satisfies(a -> {
            assertThat(a.get("attempt_number")).isEqualTo(1);
            assertThat(a.get("status_code")).isEqualTo(200);
            assertThat(a.get("error")).isNull();
        });
        assertThat(receiver.hits()).hasSize(1);
        var hit = receiver.hits().get(0);
        String storedPayload = jdbc.queryForObject("select payload from events where id = ?", String.class, eventId);
        assertThat(hit.body()).isEqualTo(storedPayload);
        assertThat(hit.headers().get("relay-event-id")).isEqualTo(eventId.toString());
        assertThat(hit.headers().get("relay-delivery-id")).isEqualTo(deliveryId(eventId).toString());
        assertThat(hit.headers().get("relay-attempt")).isEqualTo("1");
        long ts = Long.parseLong(hit.headers().get("relay-timestamp"));
        assertThat(hit.headers().get("relay-signature"))
                .isEqualTo(signer.sign(secretBytes(endpoint), ts, hit.body()));
    }

    @Test
    void retriesAfterFailuresUntilSuccess() {
        receiver.statusFor = n -> n <= 2 ? 500 : 200;
        TestApp app = createApp();
        newEndpoint(app);
        UUID eventId = publishEvent(app);

        await("delivery SUCCEEDED after retries", 20_000, () -> "SUCCEEDED".equals(deliveryStatus(eventId)));

        assertThat(attempts(eventId)).extracting(a -> a.get("attempt_number")).containsExactly(1, 2, 3);
        assertThat(attempts(eventId)).extracting(a -> a.get("status_code")).containsExactly(500, 500, 200);
        assertThat(receiver.total()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select attempt_count from deliveries where event_id = ?", Integer.class, eventId))
                .isEqualTo(3);
    }

    @Test
    void retryAfterOn429DelaysTheNextAttemptButStillCountsIt() {
        receiver.statusFor = n -> n == 1 ? 429 : 200;
        receiver.retryAfterFor = n -> n == 1 ? "3" : null;   // our own first backoff is ~1s
        TestApp app = createApp();
        newEndpoint(app);
        UUID eventId = publishEvent(app);

        await("delivered after the Retry-After wait", 20_000, () -> "SUCCEEDED".equals(deliveryStatus(eventId)));

        long gapMs = receiver.hits().get(1).atMillis() - receiver.hits().get(0).atMillis();
        assertThat(gapMs).as("waited for Retry-After, not our 1s backoff").isGreaterThanOrEqualTo(2500);
        assertThat(attempts(eventId)).extracting(a -> a.get("status_code")).containsExactly(429, 200);
    }

    @Test
    void deliveryOfACrashedWorkerIsRecoveredAfterItsLeaseExpires() {
        TestApp app = createApp();
        newEndpoint(app);
        poller.stop();                                      // set up without a race
        UUID eventId = publishEvent(app);
        // As if a worker claimed attempt 1 and then died: IN_FLIGHT, lease already expired, nothing recorded.
        jdbc.update("update deliveries set status = 'IN_FLIGHT', attempt_count = 1, "
                + "locked_until = now() - interval '1 minute' where event_id = ?", eventId);
        assertThat(attempts(eventId)).isEmpty();
        poller.start();

        await("recovered and delivered", 10_000, () -> "SUCCEEDED".equals(deliveryStatus(eventId)));

        assertThat(jdbc.queryForObject("select attempt_count from deliveries where event_id = ?", Integer.class, eventId))
                .as("the dead worker's attempt counts").isEqualTo(2);
        assertThat(attempts(eventId)).singleElement().satisfies(a -> assertThat(a.get("attempt_number")).isEqualTo(2));
        assertThat(receiver.total()).isEqualTo(1);
        assertThat(receiver.hits().get(0).headers().get("relay-attempt")).isEqualTo("2");
    }

    @Test
    void aLeaseThatHasNotExpiredIsNotStolen() throws Exception {
        TestApp app = createApp();
        newEndpoint(app);
        poller.stop();
        UUID eventId = publishEvent(app);
        jdbc.update("update deliveries set status = 'IN_FLIGHT', attempt_count = 1, "
                + "locked_until = now() + interval '1 hour' where event_id = ?", eventId);
        poller.start();

        Thread.sleep(1500);

        assertThat(receiver.total()).isZero();
        assertThat(deliveryStatus(eventId)).isEqualTo("IN_FLIGHT");
    }

    @Test
    void pausedEndpointIsNeverSentToUntilResumed() throws Exception {
        TestApp app = createApp();
        UUID endpointId = UUID.fromString(newEndpoint(app).get("id").asString());
        poller.stop();                                   // publish and pause without a race
        UUID eventId = publishEvent(app);                // fan-out only queues for ACTIVE endpoints
        pauseEndpoint(app, endpointId);
        poller.start();

        Thread.sleep(1500);   // many poll cycles

        assertThat(deliveryStatus(eventId)).isEqualTo("PENDING");
        assertThat(receiver.total()).isZero();
        assertThat(attempts(eventId)).isEmpty();

        send("POST", "/api/v1/endpoints/" + endpointId + "/resume", app.apiKey(), null, null);
        await("delivery after resume", 10_000, () -> "SUCCEEDED".equals(deliveryStatus(eventId)));
        assertThat(receiver.total()).isEqualTo(1);
    }

    @Test
    void neverMoreSendsInFlightThanWorkersAndNothingIsSentTwice() {
        receiver.delayMs = 250;
        TestApp app = createApp();
        newEndpoint(app);
        List<UUID> events = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            events.add(publishEvent(app));
        }

        await("all 20 delivered", 60_000, () -> events.stream().allMatch(e -> "SUCCEEDED".equals(deliveryStatus(e))));

        assertThat(receiver.maxInFlight()).as("pool size is 2").isEqualTo(2);
        assertThat(receiver.total()).isEqualTo(20);
        assertThat(receiver.perEvent()).hasSize(20);
        assertThat(receiver.perEvent().values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(jdbc.queryForObject("select count(*) from delivery_attempts da join deliveries d on d.id = da.delivery_id "
                + "join events e on e.id = d.event_id where e.app_id = ?", Long.class, app.id())).isEqualTo(20);
    }

    @Test
    void deliveryBeyondTheAttemptLimitIsDeadWithoutAnyHttpCall() {
        TestApp app = createApp();
        newEndpoint(app);
        poller.stop();                                        // set up without a race
        UUID eventId = publishEvent(app);
        // Schedule of 3 allows 4 attempts, so the next claim (attempt 5) is over the limit.
        jdbc.update("update deliveries set attempt_count = 4 where event_id = ?", eventId);
        poller.start();

        await("delivery DEAD", 10_000, () -> "DEAD".equals(deliveryStatus(eventId)));

        assertThat(receiver.total()).isZero();
        assertThat(attempts(eventId)).singleElement().satisfies(a -> {
            assertThat(a.get("attempt_number")).isEqualTo(5);
            assertThat(a.get("error")).isEqualTo("max attempts exceeded after lease expiry");
            assertThat(a.get("status_code")).isNull();
        });
    }

    @Test
    void gracefulStopWaitsForTheInFlightSendAndRecordsItsResult() {
        receiver.delayMs = 1500;
        TestApp app = createApp();
        newEndpoint(app);
        UUID eventId = publishEvent(app);
        await("send in flight", 10_000, () -> receiver.inFlight() == 1);

        long started = System.currentTimeMillis();
        poller.stop();
        long waited = System.currentTimeMillis() - started;

        assertThat(poller.isRunning()).isFalse();
        assertThat(waited).as("stop() waited for the send").isGreaterThan(500);
        assertThat(receiver.completed()).isEqualTo(1);
        assertThat(deliveryStatus(eventId)).isEqualTo("SUCCEEDED");   // no waiting: stop() already returned
        assertThat(attempts(eventId)).hasSize(1);
    }

    @Test
    void stopThatExceedsItsDeadlineInterruptsAndLeavesTheLeaseForRecovery() {
        props.getWorker().setShutdownTimeoutSeconds(1);
        try {
            receiver.delayMs = 4000;
            TestApp app = createApp();
            newEndpoint(app);
            UUID eventId = publishEvent(app);
            await("send in flight", 10_000, () -> receiver.inFlight() == 1);

            long started = System.currentTimeMillis();
            poller.stop();
            long waited = System.currentTimeMillis() - started;

            assertThat(waited).isLessThan(3000);
            assertThat(deliveryStatus(eventId)).isEqualTo("IN_FLIGHT");
            assertThat(attempts(eventId)).isEmpty();
            assertThat(jdbc.queryForObject("select locked_until is not null from deliveries where event_id = ?",
                    Boolean.class, eventId)).isTrue();
        } finally {
            props.getWorker().setShutdownTimeoutSeconds(30);
        }
    }
}
