package com.relay.server.endpoint;

import java.time.Instant;
import java.util.UUID;

/**
 * Response when reading an endpoint.
 * Does NOT include the signing secret.
 */
public record EndpointResponse(
        UUID id,
        String url,
        String description,
        String eventTypeFilter,
        EndpointStatus status,
        Integer consecutiveFailures,
        String pausedReason,
        Instant createdAt,
        Instant updatedAt
) {}