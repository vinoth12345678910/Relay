package com.relay.server.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to register an endpoint.
 */
public record CreateEndpointRequest(
        @NotBlank(message = "url is required")
        String url,

        String description,

        @Pattern(regexp = "^[a-z0-9._*-]+$", message = "invalid event type filter")
        String eventTypeFilter
) {}