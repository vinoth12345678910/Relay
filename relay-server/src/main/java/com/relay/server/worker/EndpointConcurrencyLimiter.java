// File: relay-server/src/main/java/com/relay/server/worker/EndpointConcurrencyLimiter.java
package com.relay.server.worker;

import com.relay.server.config.RelayProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * In-process limit on simultaneous sends to one endpoint, so a single slow receiver cannot occupy
 * every worker and starve the others (head-of-line blocking). Never blocks: a caller either gets in
 * or is told the door is full and hands the delivery back.
 *
 * <p>The limit is per application instance; with N instances the effective cap is N times higher.
 * The map keeps one small semaphore per endpoint ever seen.
 */
@Component
public class EndpointConcurrencyLimiter {

    private final int max;
    private final ConcurrentHashMap<UUID, Semaphore> doors = new ConcurrentHashMap<>();

    public EndpointConcurrencyLimiter(RelayProperties props) {
        this.max = props.getPerEndpointMaxConcurrency();
    }

    /** Takes a slot if one is free. Every {@code true} must be paired with {@link #leave}. */
    public boolean tryEnter(UUID endpointId) {
        return doors.computeIfAbsent(endpointId, k -> new Semaphore(max)).tryAcquire();
    }

    /** Frees a slot taken by a successful {@link #tryEnter}. */
    public void leave(UUID endpointId) {
        Semaphore door = doors.get(endpointId);
        if (door != null) {
            door.release();
        }
    }
}
