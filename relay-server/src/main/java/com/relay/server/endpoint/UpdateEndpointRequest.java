// File: relay-server/src/main/java/com/relay/server/endpoint/UpdateEndpointRequest.java
package com.relay.server.endpoint;

import jakarta.validation.constraints.Pattern;

/**
 * Partial update of an endpoint. Null fields are left unchanged.
 */
public record UpdateEndpointRequest(
        String url,

        String description,

        @Pattern(regexp = "^[a-z0-9._*-]+$", message = "invalid event type filter")
        String eventTypeFilter
) {}
