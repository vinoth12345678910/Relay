// File: relay-server/src/main/java/com/relay/server/endpoint/PauseEndpointRequest.java
package com.relay.server.endpoint;

import jakarta.validation.constraints.Size;

/**
 * Optional body for pausing an endpoint.
 */
public record PauseEndpointRequest(
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason
) {}
