// File: relay-server/src/main/java/com/relay/server/worker/DeliveryPoller.java
package com.relay.server.worker;

import com.relay.server.config.RelayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Claims due deliveries and sends them on a bounded worker pool.
 *
 * <p>One dedicated thread claims, so runs can never overlap. A semaphore with one permit per worker
 * thread tracks free slots: it claims at most as many deliveries as there are free slots, and takes
 * a permit for each one before handing it to the pool. A send therefore never waits in a queue while
 * holding a lease. Sending happens on the pool, never inside a transaction; claiming, loading and
 * recording are each their own short transaction.
 *
 * <p>Runs as a {@link SmartLifecycle}. Disable with {@code relay.worker.enabled=false}. On stop it
 * stops claiming, waits up to {@code relay.worker.shutdown-timeout-seconds} for in-flight sends, then
 * interrupts them. Deliveries left unfinished are recovered by lease expiry.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "relay.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DeliveryPoller implements SmartLifecycle {

    private static final long NO_FREE_SLOT_WAIT_MS = 20;
    /** A delivery turned away by a full endpoint door is tried again very soon; a long wait would throttle a busy endpoint. */
    private static final Duration DOOR_FULL_RETRY = Duration.ofMillis(250);

    private final DeliveryClaimer claimer;
    private final DeliveryContextLoader contextLoader;
    private final DeliverySender sender;
    private final DeliveryResultHandler resultHandler;
    private final EndpointConcurrencyLimiter limiter;
    private final BackoffPolicy backoffPolicy;
    private final RelayProperties props;
    private final Clock clock;

    private final Object lifecycleLock = new Object();
    private volatile boolean running;
    private ExecutorService workers;
    private Thread pollerThread;

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (running) {
                return;
            }
            int size = props.getWorkerPoolSize();
            Semaphore permits = new Semaphore(size);
            ExecutorService pool = Executors.newFixedThreadPool(size, namedThreads("relay-delivery-worker-"));
            running = true;
            workers = pool;
            pollerThread = new Thread(() -> pollLoop(permits, pool), "relay-delivery-poller");
            pollerThread.setDaemon(true);
            pollerThread.start();
            log.info("Delivery poller started: {} workers, batch size {}", size, props.getBatchSize());
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            if (!running) {
                return;
            }
            running = false;
            long deadlineMs = TimeUnit.SECONDS.toMillis(props.getWorker().getShutdownTimeoutSeconds());
            long deadlineAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMs);
            log.info("Delivery poller stopping: waiting up to {} ms for in-flight sends", deadlineMs);
            try {
                pollerThread.interrupt();
                pollerThread.join(deadlineMs);
                workers.shutdown();
                long remainingNanos = Math.max(0, deadlineAt - System.nanoTime());
                if (!workers.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                    log.warn("Delivery poller deadline reached; interrupting in-flight sends (recovered by lease expiry)");
                    workers.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                workers.shutdownNow();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollLoop(Semaphore permits, ExecutorService pool) {
        while (running) {
            try {
                int free = permits.availablePermits();
                if (free == 0) {
                    pause(NO_FREE_SLOT_WAIT_MS);
                    continue;
                }
                int limit = Math.min(free, props.getBatchSize());
                List<ClaimedDelivery> batch = claimer.claimBatch(limit);
                for (ClaimedDelivery claimed : batch) {
                    permits.acquireUninterruptibly();   // cannot block: this thread is the only taker
                    submit(pool, permits, claimed);
                }
                if (batch.size() < limit) {
                    pause(props.getPollIntervalMs());
                }
            } catch (Throwable t) {
                if (running) {
                    log.error("Delivery poll cycle failed: {}", t.getClass().getSimpleName());
                    pause(props.getPollIntervalMs());
                }
            }
        }
    }

    private void submit(ExecutorService pool, Semaphore permits, ClaimedDelivery claimed) {
        try {
            pool.execute(() -> {
                try {
                    process(claimed);
                } finally {
                    permits.release();
                }
            });
        } catch (RejectedExecutionException e) {
            permits.release();
            log.warn("Delivery {} claimed but pool is closed; it will be recovered by lease expiry", claimed.id());
        }
    }

    /** One delivery, start to finish. Nothing thrown here may escape: one bad delivery must not stop the pool. */
    private void process(ClaimedDelivery claimed) {
        try {
            if (claimed.relativeAttempt() > backoffPolicy.maxAttempts()) {   // budget restarts at each replay
                resultHandler.markDeadExhausted(claimed);
                return;
            }
            if (!limiter.tryEnter(claimed.endpointId())) {
                // Door is full: hand the delivery back (no attempt counted) and free this worker for other endpoints.
                resultHandler.release(claimed, DOOR_FULL_RETRY);
                return;
            }
            try {
                sendAndRecord(claimed);
            } finally {
                limiter.leave(claimed.endpointId());
            }
        } catch (Throwable t) {
            // Class name only: messages from lower layers could carry data we must not log.
            log.error("Delivery {} attempt {} could not be processed: {}",
                    claimed.id(), claimed.attemptNumber(), t.getClass().getSimpleName());
        }
    }

    /** Load, send and record one delivery. Runs on a worker thread, holding a slot of the endpoint's door. */
    private void sendAndRecord(ClaimedDelivery claimed) {
        Optional<SendRequest> request = contextLoader.load(claimed);
        if (request.isEmpty()) {
            resultHandler.release(claimed);
            return;
        }
        Instant startedAt = clock.instant();
        SendResult result = sender.send(request.get());
        if (Thread.currentThread().isInterrupted()) {
            log.warn("Delivery {} attempt {} interrupted by shutdown; recovered by lease expiry",
                    claimed.id(), claimed.attemptNumber());
            return;
        }
        resultHandler.apply(claimed, startedAt, result);
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static java.util.concurrent.ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread t = new Thread(runnable, prefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
