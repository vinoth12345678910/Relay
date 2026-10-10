// File: relay-server/src/test/java/com/relay/server/integration/DeliveryResultHandlerIT.java
package com.relay.server.integration;

import com.relay.server.delivery.AttemptRepository;
import com.relay.server.delivery.Delivery;
import com.relay.server.delivery.DeliveryAttempt;
import com.relay.server.delivery.DeliveryRepository;
import com.relay.server.delivery.DeliveryStatus;
import com.relay.server.worker.ClaimedDelivery;
import com.relay.server.worker.DeliveryResultHandler;
import com.relay.server.worker.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Result handling against real Postgres, with a fake clock and a pinned random source. */
class DeliveryResultHandlerIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    /** nextDouble() is settable; 0.5 means "no jitter". */
    static class StubRandom extends Random {
        volatile double value = 0.5;

        @Override
        public double nextDouble() {
            return value;
        }
    }

    @TestConfiguration
    static class FakeTimeAndRandom {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }

        @Bean
        @Primary
        StubRandom testRandom() {
            return new StubRandom();
        }
    }

    @Autowired
    private DeliveryResultHandler handler;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private AttemptRepository attemptRepository;
    @Autowired
    private MutableClock clock;
    @Autowired
    private StubRandom random;
    @Autowired
    private com.relay.server.config.RelayProperties props;

    @BeforeEach
    void resetTimeAndJitter() {
        clock.set(NOW);
        random.value = 0.5;
    }

    /** Creates a real delivery and puts it in the state a worker leaves it in after claiming it. */
    private ClaimedDelivery claimedAtAttempt(int attempt) {
        TestApp app = createApp();
        createEndpoint(app, "*");
        publish(app, "order.paid", "{\"a\":1}", null);
        UUID id = jdbc.queryForObject(
                "select d.id from deliveries d join events e on e.id = d.event_id where e.app_id = ?", UUID.class, app.id());
        jdbc.update("update deliveries set status = 'IN_FLIGHT', attempt_count = ?, locked_until = ? where id = ?",
                attempt, java.sql.Timestamp.from(NOW.plusSeconds(30)), id);
        Delivery d = deliveryRepository.findById(id).orElseThrow();
        return new ClaimedDelivery(id, d.getEventId(), d.getEndpointId(), attempt, 0);
    }

    private static SendResult ok() {
        return new SendResult(true, 200, null, 42, "{\"received\":true}");
    }

    private static SendResult failed(Integer status, String error, String snippet) {
        return new SendResult(false, status, error, 17, snippet);
    }

    private Delivery reload(ClaimedDelivery c) {
        return deliveryRepository.findById(c.id()).orElseThrow();
    }

    @Test
    void successMarksSucceededAndClearsTheLease() {
        ClaimedDelivery c = claimedAtAttempt(1);
        Instant before = reload(c).getNextAttemptAt();
        clock.set(NOW.plusSeconds(5));

        handler.apply(c, NOW.plusSeconds(4), ok());

        Delivery d = reload(c);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(d.getLockedUntil()).isNull();
        assertThat(d.getLastStatusCode()).isEqualTo(200);
        assertThat(d.getUpdatedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(d.getNextAttemptAt()).isEqualTo(before);
        DeliveryAttempt a = attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id()).get(0);
        assertThat(a.getAttemptNumber()).isEqualTo(1);
        assertThat(a.getStatusCode()).isEqualTo(200);
        assertThat(a.getError()).isNull();
        assertThat(a.getDurationMs()).isEqualTo(42);
        assertThat(a.getStartedAt()).isEqualTo(NOW.plusSeconds(4));
        assertThat(a.getCreatedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void firstFailureSchedulesTenSecondsLater() {
        ClaimedDelivery c = claimedAtAttempt(1);

        handler.apply(c, NOW, failed(500, "HTTP 500", "boom"));

        Delivery d = reload(c);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(d.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(d.getLockedUntil()).isNull();
        assertThat(d.getLastStatusCode()).isEqualTo(500);
        assertThat(d.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void retryDelayStaysWithinTheJitterBand() {
        random.value = 0.0;
        ClaimedDelivery low = claimedAtAttempt(1);
        handler.apply(low, NOW, failed(503, "HTTP 503", null));
        random.value = Math.nextDown(1.0);
        ClaimedDelivery high = claimedAtAttempt(1);
        handler.apply(high, NOW, failed(503, "HTTP 503", null));

        assertThat(reload(low).getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMillis(8000)));
        assertThat(reload(high).getNextAttemptAt()).isBetween(NOW.plusMillis(11999), NOW.plusMillis(12000));
    }

    @Test
    void lastScheduledStepIsTwelveHours() {
        ClaimedDelivery c = claimedAtAttempt(8);

        handler.apply(c, NOW, failed(500, "HTTP 500", null));

        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(reload(c).getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void ninthFailureIsDead() {
        ClaimedDelivery c = claimedAtAttempt(9);

        handler.apply(c, NOW, failed(500, "HTTP 500", null));

        Delivery d = reload(c);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(d.getLockedUntil()).isNull();
        assertThat(d.getLastStatusCode()).isEqualTo(500);
        assertThat(d.getAttemptCount()).isEqualTo(9);
    }

    @Test
    void staleLeaseRecordsTheAttemptButLeavesTheDeliveryAlone() {
        ClaimedDelivery slowWorker = claimedAtAttempt(1);
        // Lease expired and a second worker re-claimed it: attempt 2 is now in flight.
        jdbc.update("update deliveries set attempt_count = 2, locked_until = ?, updated_at = ? where id = ?",
                java.sql.Timestamp.from(NOW.plusSeconds(60)), java.sql.Timestamp.from(NOW), slowWorker.id());
        Delivery before = reload(slowWorker);

        handler.apply(slowWorker, NOW, failed(200 + 300, "HTTP 500", "late"));

        Delivery after = reload(slowWorker);
        assertThat(after.getStatus()).isEqualTo(DeliveryStatus.IN_FLIGHT);
        assertThat(after.getAttemptCount()).isEqualTo(2);
        assertThat(after.getLockedUntil()).isEqualTo(before.getLockedUntil());
        assertThat(after.getNextAttemptAt()).isEqualTo(before.getNextAttemptAt());
        assertThat(after.getLastStatusCode()).isEqualTo(before.getLastStatusCode());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        List<DeliveryAttempt> attempts = attemptRepository.findByDeliveryIdOrderByAttemptNumber(slowWorker.id());
        assertThat(attempts).singleElement().satisfies(a -> {
            assertThat(a.getAttemptNumber()).isEqualTo(1);
            assertThat(a.getStatusCode()).isEqualTo(500);
        });
    }

    @Test
    void lateSuccessForAnAlreadyFinishedDeliveryDoesNotReopenIt() {
        ClaimedDelivery c = claimedAtAttempt(1);
        jdbc.update("update deliveries set status = 'DEAD', attempt_count = 3 where id = ?", c.id());

        handler.apply(c, NOW, ok());

        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id())).hasSize(1);
    }

    @Test
    void recordingTheSameAttemptTwiceIsIgnoredGracefully() {
        ClaimedDelivery c = claimedAtAttempt(1);
        handler.apply(c, NOW, failed(500, "HTTP 500", null));
        Delivery afterFirst = reload(c);

        handler.apply(c, NOW, ok());   // must not throw, must not apply a second time

        assertThat(attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id())).hasSize(1);
        assertThat(reload(c).getStatus()).isEqualTo(afterFirst.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id()).get(0).getStatusCode()).isEqualTo(500);
    }

    @Test
    void attemptRowKeepsStatusErrorAndATruncatedSnippet() {
        ClaimedDelivery c = claimedAtAttempt(1);
        String huge = "x".repeat(10_000);

        handler.apply(c, NOW, failed(502, "HTTP 502", huge));

        DeliveryAttempt a = attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id()).get(0);
        assertThat(a.getStatusCode()).isEqualTo(502);
        assertThat(a.getError()).isEqualTo("HTTP 502");
        assertThat(a.getResponseSnippet()).hasSize(4096);
        assertThat(a.getDurationMs()).isEqualTo(17);
    }

    @Test
    void timeoutWithoutAResponseStoresNullStatusAndTheError() {
        ClaimedDelivery c = claimedAtAttempt(1);

        handler.apply(c, NOW, failed(null, "HttpTimeoutException: request timed out", null));

        DeliveryAttempt a = attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id()).get(0);
        assertThat(a.getStatusCode()).isNull();
        assertThat(a.getError()).contains("HttpTimeoutException");
        assertThat(a.getResponseSnippet()).isNull();
        assertThat(reload(c).getLastStatusCode()).isNull();
        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void nulCharactersInAResponseBodyDoNotBreakRecording() {
        ClaimedDelivery c = claimedAtAttempt(1);

        handler.apply(c, NOW, failed(500, "HTTP 500", "ab\u0000cd"));

        assertThat(attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id()).get(0).getResponseSnippet())
                .isEqualTo("abcd");
        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void releaseUndoesTheClaimWithoutConsumingAnAttempt() {
        ClaimedDelivery c = claimedAtAttempt(3);

        handler.release(c);

        Delivery d = reload(c);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(d.getAttemptCount()).isEqualTo(2);
        assertThat(d.getLockedUntil()).isNull();
        assertThat(d.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(d.getUpdatedAt()).isEqualTo(NOW);
        assertThat(attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id())).isEmpty();
    }

    @Test
    void releaseCanUseAShorterDelay() {
        ClaimedDelivery c = claimedAtAttempt(2);

        handler.release(c, Duration.ofMillis(250));

        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(reload(c).getAttemptCount()).isEqualTo(1);
        assertThat(reload(c).getNextAttemptAt()).isEqualTo(NOW.plusMillis(250));
    }

    @Test
    void releaseIsIgnoredWhenAnotherWorkerHasTheDelivery() {
        ClaimedDelivery c = claimedAtAttempt(1);
        jdbc.update("update deliveries set attempt_count = 2 where id = ?", c.id());

        handler.release(c);

        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.IN_FLIGHT);
        assertThat(reload(c).getAttemptCount()).isEqualTo(2);
    }

    @Test
    void exhaustedDeliveryIsDeadWithAnAttemptRowAndIsIdempotent() {
        ClaimedDelivery c = claimedAtAttempt(10);

        handler.markDeadExhausted(c);
        handler.markDeadExhausted(c);   // second call: stale (already DEAD), must not throw or duplicate

        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(reload(c).getLockedUntil()).isNull();
        assertThat(attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id())).singleElement().satisfies(a -> {
            assertThat(a.getAttemptNumber()).isEqualTo(10);
            assertThat(a.getError()).isEqualTo("max attempts exceeded after lease expiry");
            assertThat(a.getStatusCode()).isNull();
        });
    }

    @Test
    void exhaustedDeliveryKeepsAnExistingAttemptRowForThatNumber() {
        ClaimedDelivery c = claimedAtAttempt(10);
        handler.apply(c, NOW, failed(500, "HTTP 500", null));   // a row for attempt 10 already exists (and goes DEAD)
        jdbc.update("update deliveries set status = 'IN_FLIGHT' where id = ?", c.id());

        handler.markDeadExhausted(c);

        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(attemptRepository.findByDeliveryIdOrderByAttemptNumber(c.id())).singleElement()
                .satisfies(a -> assertThat(a.getStatusCode()).isEqualTo(500));
    }

    // ---------- circuit breaker wiring ----------

    private record EndpointRow(String status, int failures, String reason) {}

    private EndpointRow endpointOf(ClaimedDelivery c) {
        return jdbc.queryForObject("select status, consecutive_failures, paused_reason from endpoints where id = ?",
                (rs, i) -> new EndpointRow(rs.getString(1), rs.getInt(2), rs.getString(3)), c.endpointId());
    }

    @Test
    void failuresCountAndASuccessResetsTheStreak() {
        ClaimedDelivery c = claimedAtAttempt(1);

        handler.apply(c, NOW, failed(500, "HTTP 500", null));
        assertThat(endpointOf(c).failures()).isEqualTo(1);

        jdbc.update("update deliveries set status = 'IN_FLIGHT', attempt_count = 2 where id = ?", c.id());
        handler.apply(new ClaimedDelivery(c.id(), c.eventId(), c.endpointId(), 2, 0), NOW, ok());

        assertThat(endpointOf(c).failures()).isZero();
        assertThat(endpointOf(c).status()).isEqualTo("ACTIVE");
    }

    @Test
    void thresholdFailuresThroughTheHandlerPauseTheEndpoint() {
        int saved = props.getBreakerThreshold();
        props.setBreakerThreshold(2);
        try {
            ClaimedDelivery first = claimedAtAttempt(1);
            handler.apply(first, NOW, failed(500, "HTTP 500", null));
            assertThat(endpointOf(first).status()).isEqualTo("ACTIVE");

            jdbc.update("update deliveries set status = 'IN_FLIGHT', attempt_count = 2 where id = ?", first.id());
            handler.apply(new ClaimedDelivery(first.id(), first.eventId(), first.endpointId(), 2, 0), NOW, failed(502, "HTTP 502", null));

            assertThat(endpointOf(first)).isEqualTo(new EndpointRow("PAUSED", 2, "circuit breaker: 2 consecutive failures"));
            assertThat(reload(first).getStatus()).isEqualTo(DeliveryStatus.PENDING);   // the delivery itself still retries normally
        } finally {
            props.setBreakerThreshold(saved);
        }
    }

    @Test
    void goneResponsePausesImmediatelyWithTheReasonAndTheDeliveryStillRetries() {
        ClaimedDelivery c = claimedAtAttempt(1);

        handler.apply(c, NOW, failed(410, "HTTP 410", null));

        assertThat(endpointOf(c).status()).isEqualTo("PAUSED");
        assertThat(endpointOf(c).reason()).isEqualTo("receiver returned 410 Gone");
        assertThat(endpointOf(c).failures()).isEqualTo(1);
        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(reload(c).getLastStatusCode()).isEqualTo(410);
    }

    @Test
    void staleAndDuplicateResultsAreNotCounted() {
        ClaimedDelivery stale = claimedAtAttempt(1);
        jdbc.update("update deliveries set attempt_count = 2 where id = ?", stale.id());
        handler.apply(stale, NOW, failed(500, "HTTP 500", null));
        assertThat(endpointOf(stale).failures()).as("stale result").isZero();

        ClaimedDelivery dup = claimedAtAttempt(1);
        handler.apply(dup, NOW, failed(500, "HTTP 500", null));
        handler.apply(dup, NOW, failed(500, "HTTP 500", null));
        assertThat(endpointOf(dup).failures()).as("duplicate result").isEqualTo(1);
    }

    // ---------- Retry-After ----------

    private static SendResult busy(int status, Duration retryAfter) {
        return new SendResult(false, status, "HTTP " + status, 17, null, retryAfter);
    }

    @Test
    void retryAfterLongerThanTheBackoffWins() {
        ClaimedDelivery c = claimedAtAttempt(1);

        handler.apply(c, NOW, busy(429, Duration.ofMinutes(5)));

        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(reload(c).getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(reload(c).getAttemptCount()).as("still costs an attempt").isEqualTo(1);
    }

    @Test
    void retryAfterShorterThanTheBackoffNeverShortensIt() {
        ClaimedDelivery c = claimedAtAttempt(1);

        handler.apply(c, NOW, busy(503, Duration.ofSeconds(2)));

        assertThat(reload(c).getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));   // our own first step
    }

    @Test
    void retryAfterDoesNotReviveADeliveryThatIsOutOfAttempts() {
        ClaimedDelivery c = claimedAtAttempt(9);

        handler.apply(c, NOW, busy(429, Duration.ofMinutes(5)));

        assertThat(reload(c).getStatus()).isEqualTo(DeliveryStatus.DEAD);
    }

    @Test
    void noRetryAfterBehavesAsBefore() {
        ClaimedDelivery c = claimedAtAttempt(2);

        handler.apply(c, NOW, busy(429, null));

        assertThat(reload(c).getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));   // second step
    }
}
