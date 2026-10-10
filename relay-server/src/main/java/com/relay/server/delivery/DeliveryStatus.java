// File: relay-server/src/main/java/com/relay/server/delivery/DeliveryStatus.java
package com.relay.server.delivery;

/**
 * Lifecycle of a delivery.
 * PENDING: waiting for its next attempt. IN_FLIGHT: claimed by a worker.
 * SUCCEEDED: receiver acknowledged. DEAD: retries exhausted.
 */
public enum DeliveryStatus {
    PENDING,
    IN_FLIGHT,
    SUCCEEDED,
    DEAD
}
