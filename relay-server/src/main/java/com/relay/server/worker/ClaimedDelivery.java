package com.relay.server.worker;

import java.util.UUID;

/** A delivery this worker now holds a lease on. attemptNumber is already incremented. */
public record ClaimedDelivery(UUID id, UUID eventId, UUID endpointId,
                              int attemptNumber, int attemptBase) {

    /** Retry budget position: 1 for the first try after creation or replay. */
    public int relativeAttempt() {
        return attemptNumber - attemptBase;
    }
}