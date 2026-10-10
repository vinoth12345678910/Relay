// File: relay-server/src/main/java/com/relay/server/event/EventResponse.java
package com.relay.server.event;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public view of an event with its deliveries. The payload is parsed back from the stored
 * string for display only; deliveries always sign the stored string, never this node.
 */
public record EventResponse(
        UUID id,
        String type,
        JsonNode payload,
        Instant createdAt,
        List<DeliverySummary> deliveries
) {}
