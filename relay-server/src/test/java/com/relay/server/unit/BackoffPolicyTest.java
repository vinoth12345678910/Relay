// File: relay-server/src/test/java/com/relay/server/unit/BackoffPolicyTest.java
package com.relay.server.unit;

import com.relay.server.worker.BackoffPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackoffPolicyTest {

    private static final String DEFAULT = "10,30,120,600,1800,7200,21600,43200";
    private final BackoffPolicy policy = BackoffPolicy.fromSeconds(DEFAULT, 0.2);

    /** A generator whose nextDouble() is fixed, to pin the jitter exactly. */
    private static RandomGenerator fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    @ParameterizedTest(name = "attempt {0} failed -> base {1}s")
    @CsvSource({"1,10", "2,30", "3,120", "4,600", "5,1800", "6,7200", "7,21600", "8,43200"})
    void eachStepUsesItsBaseWhenJitterIsNeutral(int attempt, long baseSeconds) {
        Optional<Duration> delay = policy.delayAfterFailure(attempt, fixed(0.5));

        assertThat(delay).contains(Duration.ofSeconds(baseSeconds));
    }

    @ParameterizedTest(name = "attempt {0}: jitter stays within +-20% of {1}s")
    @CsvSource({"1,10", "2,30", "3,120", "4,600", "5,1800", "6,7200", "7,21600", "8,43200"})
    void jitterBoundsAtMinimumAndMaximum(int attempt, long baseSeconds) {
        long base = baseSeconds * 1000;

        Duration min = policy.delayAfterFailure(attempt, fixed(0.0)).orElseThrow();
        Duration almostMax = policy.delayAfterFailure(attempt, fixed(Math.nextDown(1.0))).orElseThrow();

        assertThat(min.toMillis()).isEqualTo(Math.round(base * 0.8));
        assertThat(almostMax.toMillis()).isBetween(Math.round(base * 1.2) - 1, Math.round(base * 1.2));
    }

    @Test
    void randomJitterNeverLeavesTheBandOrGoesNegative() {
        Random rnd = new Random(42);
        for (int attempt = 1; attempt <= 8; attempt++) {
            long base = policy.delayAfterFailure(attempt, fixed(0.5)).orElseThrow().toMillis();
            for (int i = 0; i < 2000; i++) {
                long ms = policy.delayAfterFailure(attempt, rnd).orElseThrow().toMillis();
                assertThat(ms).isBetween(Math.round(base * 0.8), Math.round(base * 1.2)).isPositive();
            }
        }
    }

    @Test
    void emptyAfterTheLastStepMeansDead() {
        assertThat(policy.maxAttempts()).isEqualTo(9);
        assertThat(policy.delayAfterFailure(8, fixed(0.5))).isPresent();
        assertThat(policy.delayAfterFailure(9, fixed(0.5))).isEmpty();
        assertThat(policy.delayAfterFailure(50, fixed(0.5))).isEmpty();
    }

    @Test
    void rejectsBadInputs() {
        assertThatThrownBy(() -> policy.delayAfterFailure(0, fixed(0.5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BackoffPolicy(List.of(), 0.2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BackoffPolicy(List.of(Duration.ofSeconds(1)), 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BackoffPolicy(List.of(Duration.ofSeconds(1)), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesSecondsWithWhitespaceAndSingleStep() {
        BackoffPolicy p = BackoffPolicy.fromSeconds(" 5 , 15 ", 0.0);

        assertThat(p.maxAttempts()).isEqualTo(3);
        assertThat(p.delayAfterFailure(2, fixed(0.9))).contains(Duration.ofSeconds(15));
    }
}
