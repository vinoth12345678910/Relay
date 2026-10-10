// File: relay-server/src/main/java/com/relay/server/delivery/DeliveryDetail.java
package com.relay.server.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of one delivery. {@code attemptBase} is where the current retry budget started
 * (it moves forward on replay); {@code attemptCount} only ever grows.
 */
public record DeliveryDetail(
        UUID id, UUID eventId, UUID endpointId, String status,
        int attemptCount, int attemptBase,
        Instant nextAttemptAt, Integer lastStatusCode,
        Instant createdAt, Instant updatedAt) {}
