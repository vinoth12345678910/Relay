// File: relay-server/src/main/java/com/relay/server/event/DeliverySummary.java
package com.relay.server.event;

import com.relay.server.delivery.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of one delivery of an event.
 */
public record DeliverySummary(
        UUID id,
        UUID endpointId,
        DeliveryStatus status,
        Integer attemptCount,
        Instant nextAttemptAt,
        Integer lastStatusCode
) {}
