package com.relay.server.endpoint;

/**
 * Status of an endpoint.
 * ACTIVE: actively delivering
 * PAUSED: queued, not delivering (manual pause or circuit breaker)
 * DISABLED: soft-deleted, no longer delivers
 */
public enum EndpointStatus {
    ACTIVE,
    PAUSED,
    DISABLED
}