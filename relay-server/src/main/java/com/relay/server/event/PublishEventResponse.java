// File: relay-server/src/main/java/com/relay/server/event/PublishEventResponse.java
package com.relay.server.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Response to a publish. {@code replayed} is true when an earlier publish with the same
 * idempotency key was returned instead of creating a new event.
 */
public record PublishEventResponse(
        UUID id,
        String type,
        Instant createdAt,
        long deliveryCount,
        boolean replayed
) {}
