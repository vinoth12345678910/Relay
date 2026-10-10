// File: relay-server/src/main/java/com/relay/server/endpoint/CreateEndpointResponse.java
package com.relay.server.endpoint;

import java.time.Instant;
import java.util.UUID;

/**
 * Response when creating an endpoint. Includes the plaintext signing secret,
 * which is shown only here and cannot be retrieved later.
 */
public record CreateEndpointResponse(
        UUID id,
        String url,
        String description,
        String eventTypeFilter,
        EndpointStatus status,
        Integer consecutiveFailures,
        String pausedReason,
        Instant createdAt,
        Instant updatedAt,
        String signingSecret
) {

    /** Combines the regular endpoint view with the one-time secret. */
    public static CreateEndpointResponse of(EndpointResponse e, String signingSecret) {
        return new CreateEndpointResponse(e.id(), e.url(), e.description(), e.eventTypeFilter(),
                e.status(), e.consecutiveFailures(), e.pausedReason(), e.createdAt(), e.updatedAt(),
                signingSecret);
    }
}
