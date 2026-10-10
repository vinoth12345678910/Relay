// File: relay-server/src/main/java/com/relay/server/worker/BackoffPolicy.java
package com.relay.server.worker;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Retry delays. Pure: randomness is passed in, time is not involved.
 *
 * <p>Attempt N failing waits {@code schedule[N-1]} (plus or minus jitter). Once N exceeds the
 * schedule there is no next delay and the delivery is dead, so
 * {@code maxAttempts = schedule.size() + 1}.
 */
public class BackoffPolicy {

    private final List<Duration> schedule;
    private final double jitterFraction;

    public BackoffPolicy(List<Duration> schedule, double jitterFraction) {
        if (schedule == null || schedule.isEmpty()) {
            throw new IllegalArgumentException("retry schedule must not be empty");
        }
        if (jitterFraction < 0 || jitterFraction >= 1) {
            throw new IllegalArgumentException("jitter fraction must be in [0, 1)");
        }
        this.schedule = List.copyOf(schedule);
        this.jitterFraction = jitterFraction;
    }

    /** Builds a policy from a comma-separated list of seconds, e.g. {@code "10,30,120"}. */
    public static BackoffPolicy fromSeconds(String commaSeparatedSeconds, double jitterFraction) {
        List<Duration> schedule = Arrays.stream(commaSeparatedSeconds.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .map(Duration::ofSeconds)
                .toList();
        return new BackoffPolicy(schedule, jitterFraction);
    }

    /**
     * Delay before the next attempt after attempt {@code attemptNumber} (1-based) failed,
     * or empty if retries are exhausted and the delivery is dead.
     */
    public Optional<Duration> delayAfterFailure(int attemptNumber, RandomGenerator rnd) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber starts at 1");
        }
        if (attemptNumber > schedule.size()) {
            return Optional.empty();
        }
        long baseMillis = schedule.get(attemptNumber - 1).toMillis();
        double factor = 1 + (rnd.nextDouble() * 2 - 1) * jitterFraction;   // uniform in [1-j, 1+j)
        return Optional.of(Duration.ofMillis(Math.round(baseMillis * factor)));
    }

    /** Total attempts before a delivery is dead (the first try plus one per schedule step). */
    public int maxAttempts() {
        return schedule.size() + 1;
    }
}
