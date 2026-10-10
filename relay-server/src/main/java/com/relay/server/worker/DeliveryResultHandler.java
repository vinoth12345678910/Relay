// File: relay-server/src/main/java/com/relay/server/worker/DeliveryResultHandler.java
package com.relay.server.worker;

import com.relay.server.delivery.Delivery;
import com.relay.server.delivery.DeliveryRepository;
import com.relay.server.delivery.DeliveryStateMachine;
import com.relay.server.delivery.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

/**
 * Records the outcome of one send and moves the delivery to its next state, in one short
 * transaction. No HTTP happens here: the send has already finished.
 *
 * <p>The same transaction also feeds the endpoint circuit breaker ({@link CircuitBreakerService}), but
 * only for results that actually change the delivery: duplicate and stale results are not counted.
 * <p>A 429/503 Retry-After (already parsed and capped by the sender) lengthens the next delay but never
 * shortens it, and the attempt still counts: an exhausted delivery is DEAD whatever the header says.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryResultHandler {

    private static final int MAX_SNIPPET_CHARS = 4096;
    private static final int MAX_ERROR_CHARS = 1000;
    private static final Duration RELEASE_DELAY = Duration.ofSeconds(5);
    static final String EXHAUSTED_ERROR = "max attempts exceeded after lease expiry";

    private static final String INSERT_ATTEMPT = """
            INSERT INTO delivery_attempts
                (delivery_id, attempt_number, started_at, duration_ms, status_code, error, response_snippet, created_at)
            VALUES (:deliveryId, :attemptNumber, :startedAt, :durationMs, :statusCode, :error, :snippet, :now)
            ON CONFLICT (delivery_id, attempt_number) DO NOTHING
            """;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryStateMachine stateMachine;
    private final CircuitBreakerService breaker;
    private final BackoffPolicy backoffPolicy;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final Random random;

    /** Applies the result of the attempt {@code claimed} represents. */
    @Transactional
    public void apply(ClaimedDelivery claimed, Instant startedAt, SendResult result) {
        Instant now = clock.instant();
        Optional<Delivery> locked = deliveryRepository.findForUpdate(claimed.id());
        if (locked.isEmpty()) {
            log.warn("Delivery {} no longer exists; dropping result of attempt {}", claimed.id(), claimed.attemptNumber());
            return;
        }
        Delivery delivery = locked.get();

        if (!recordAttempt(claimed, startedAt, result, now)) {
            log.debug("Attempt {} of delivery {} was already recorded", claimed.attemptNumber(), claimed.id());
            return;
        }
        if (isStale(delivery, claimed)) {
            log.warn("Stale result for delivery {} attempt {}: delivery is now {} at attempt {}; attempt recorded, delivery untouched",
                    claimed.id(), claimed.attemptNumber(), delivery.getStatus(), delivery.getAttemptCount());
            return;
        }
        if (result.success()) {
            markSucceeded(delivery, result, now);
        } else {
            markFailed(delivery, claimed, result, now);
        }
        deliveryRepository.save(delivery);
        updateBreaker(claimed, result);
    }

    /** 410 Gone pauses first so the reason says so; the failure count then leaves an existing pause alone. */
    private void updateBreaker(ClaimedDelivery claimed, SendResult result) {
        if (result.success()) {
            breaker.recordSuccess(claimed.endpointId());
            return;
        }
        if (result.statusCode() != null && result.statusCode() == 410) {
            breaker.pauseNow(claimed.endpointId(), "receiver returned 410 Gone");
        }
        breaker.recordFailure(claimed.endpointId());
    }

    /**
     * Gives a claimed delivery back without sending it (for example the endpoint was paused after the
     * claim). Undoes the attempt bump, so no attempt is consumed and no attempt row is written, and
     * retries in a few seconds. Ignored if the delivery has since been taken by someone else.
     */
    @Transactional
    public void release(ClaimedDelivery claimed) {
        release(claimed, RELEASE_DELAY);
    }

    /** Same as {@link #release(ClaimedDelivery)} but with a caller-chosen delay before the next claim. */
    @Transactional
    public void release(ClaimedDelivery claimed, Duration retryIn) {
        Instant now = clock.instant();
        Optional<Delivery> locked = deliveryRepository.findForUpdate(claimed.id());
        if (locked.isEmpty() || isStale(locked.get(), claimed)) {
            log.debug("Delivery {} attempt {} not released: gone or already taken", claimed.id(), claimed.attemptNumber());
            return;
        }
        Delivery delivery = locked.get();
        delivery.setStatus(stateMachine.transition(delivery.getStatus(), DeliveryStatus.PENDING));
        delivery.setAttemptCount(delivery.getAttemptCount() - 1);
        delivery.setLockedUntil(null);
        delivery.setNextAttemptAt(now.plus(retryIn));
        delivery.setUpdatedAt(now);
        deliveryRepository.save(delivery);
        log.info("Released delivery {} (attempt {} not sent)", claimed.id(), claimed.attemptNumber());
    }

    /**
     * Poison guard: a delivery whose claims keep expiring (for example a worker that dies on it every
     * time) must not loop forever. Marks it DEAD without sending, and records an attempt row for the
     * claimed number if there is not one already.
     */
    @Transactional
    public void markDeadExhausted(ClaimedDelivery claimed) {
        Instant now = clock.instant();
        Optional<Delivery> locked = deliveryRepository.findForUpdate(claimed.id());
        if (locked.isEmpty()) {
            return;
        }
        SendResult exhausted = new SendResult(false, null, EXHAUSTED_ERROR, 0, null);
        recordAttempt(claimed, now, exhausted, now);
        Delivery delivery = locked.get();
        if (isStale(delivery, claimed)) {
            log.warn("Delivery {} attempt {} exceeded the limit but was already taken by another worker", claimed.id(), claimed.attemptNumber());
            return;
        }
        delivery.setStatus(stateMachine.transition(delivery.getStatus(), DeliveryStatus.DEAD));
        finish(delivery, exhausted, now);
        deliveryRepository.save(delivery);
        log.warn("Delivery {} is DEAD: attempt {} exceeds the maximum", claimed.id(), claimed.attemptNumber());
    }

    /** The lease expired and another worker took the delivery, so this result must not overwrite it. */
    private boolean isStale(Delivery delivery, ClaimedDelivery claimed) {
        return delivery.getStatus() != DeliveryStatus.IN_FLIGHT
                || delivery.getAttemptCount() != claimed.attemptNumber();
    }

    private void markSucceeded(Delivery delivery, SendResult result, Instant now) {
        delivery.setStatus(stateMachine.transition(delivery.getStatus(), DeliveryStatus.SUCCEEDED));
        finish(delivery, result, now);
    }

    private void markFailed(Delivery delivery, ClaimedDelivery claimed, SendResult result, Instant now) {
        Optional<Duration> delay = backoffPolicy.delayAfterFailure(claimed.relativeAttempt(), random)
                .map(base -> longer(base, result.retryAfter()));
        if (delay.isPresent()) {
            delivery.setStatus(stateMachine.transition(delivery.getStatus(), DeliveryStatus.PENDING));
            delivery.setNextAttemptAt(now.plus(delay.get()));
        } else {
            delivery.setStatus(stateMachine.transition(delivery.getStatus(), DeliveryStatus.DEAD));
        }
        finish(delivery, result, now);
    }

    /** Retry-After can only make us wait longer, never retry sooner than our own schedule. */
    private static Duration longer(Duration base, Duration retryAfter) {
        return retryAfter != null && retryAfter.compareTo(base) > 0 ? retryAfter : base;
    }

    private void finish(Delivery delivery, SendResult result, Instant now) {
        delivery.setLockedUntil(null);
        delivery.setLastStatusCode(result.statusCode());
        delivery.setUpdatedAt(now);
    }

    /**
     * Idempotent insert. Plain INSERT would abort the whole Postgres transaction on a duplicate,
     * so ON CONFLICT DO NOTHING is used and the row count says whether it was new.
     */
    private boolean recordAttempt(ClaimedDelivery claimed, Instant startedAt, SendResult result, Instant now) {
        int inserted = jdbc.sql(INSERT_ATTEMPT)
                .param("deliveryId", claimed.id())
                .param("attemptNumber", claimed.attemptNumber())
                .param("startedAt", java.time.OffsetDateTime.ofInstant(startedAt, java.time.ZoneOffset.UTC))
                .param("durationMs", result.durationMs())
                .param("statusCode", result.statusCode(), Types.INTEGER)
                .param("error", clean(result.error(), MAX_ERROR_CHARS), Types.VARCHAR)
                .param("snippet", clean(result.responseSnippet(), MAX_SNIPPET_CHARS), Types.VARCHAR)
                .param("now", java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
                .update();
        return inserted == 1;
    }

    /** Truncates, and drops NUL characters, which Postgres TEXT cannot store (they would fail the insert). */
    private static String clean(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String withoutNul = text.replace("\u0000", "");
        return withoutNul.length() > maxChars ? withoutNul.substring(0, maxChars) : withoutNul;
    }
}
