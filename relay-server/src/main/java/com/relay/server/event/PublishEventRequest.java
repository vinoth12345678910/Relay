// File: relay-server/src/main/java/com/relay/server/event/PublishEventRequest.java
package com.relay.server.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * Request to publish an event. The type format is checked by {@link EventService}.
 */
public record PublishEventRequest(
        @NotBlank(message = "type is required")
        String type,

        @NotNull(message = "payload is required")
        JsonNode payload
) {}
