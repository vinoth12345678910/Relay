// File: relay-server/src/test/java/com/relay/server/unit/EndpointConcurrencyLimiterTest.java
package com.relay.server.unit;

import com.relay.server.config.RelayProperties;
import com.relay.server.worker.EndpointConcurrencyLimiter;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointConcurrencyLimiterTest {

    private EndpointConcurrencyLimiter limiter(int max) {
        RelayProperties props = new RelayProperties();
        props.setPerEndpointMaxConcurrency(max);
        return new EndpointConcurrencyLimiter(props);
    }

    @Test
    void letsInExactlyMaxAndRefusesTheRestWithoutBlocking() {
        EndpointConcurrencyLimiter l = limiter(2);
        UUID ep = UUID.randomUUID();

        assertThat(l.tryEnter(ep)).isTrue();
        assertThat(l.tryEnter(ep)).isTrue();
        assertThat(l.tryEnter(ep)).isFalse();
        assertThat(l.tryEnter(ep)).isFalse();
    }

    @Test
    void leavingFreesASlot() {
        EndpointConcurrencyLimiter l = limiter(1);
        UUID ep = UUID.randomUUID();
        assertThat(l.tryEnter(ep)).isTrue();
        assertThat(l.tryEnter(ep)).isFalse();

        l.leave(ep);

        assertThat(l.tryEnter(ep)).isTrue();
    }

    @Test
    void endpointsHaveIndependentDoors() {
        EndpointConcurrencyLimiter l = limiter(1);
        UUID slow = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertThat(l.tryEnter(slow)).isTrue();

        assertThat(l.tryEnter(slow)).isFalse();
        assertThat(l.tryEnter(other)).as("a full door elsewhere does not matter").isTrue();
    }

    @Test
    void leavingAnUnknownEndpointIsHarmless() {
        EndpointConcurrencyLimiter l = limiter(1);

        l.leave(UUID.randomUUID());   // never entered: must not throw or create spare capacity
        UUID ep = UUID.randomUUID();

        assertThat(l.tryEnter(ep)).isTrue();
        assertThat(l.tryEnter(ep)).isFalse();
    }

    @Test
    void neverAdmitsMoreThanMaxUnderContention() throws Exception {
        EndpointConcurrencyLimiter l = limiter(3);
        UUID ep = UUID.randomUUID();
        AtomicInteger admitted = new AtomicInteger();

        var threads = IntStream.range(0, 50).mapToObj(i -> new Thread(() -> {
            if (l.tryEnter(ep)) {
                admitted.incrementAndGet();
            }
        })).toList();
        threads.forEach(Thread::start);
        for (Thread t : threads) {
            t.join();
        }

        assertThat(admitted).hasValue(3);
    }
}
