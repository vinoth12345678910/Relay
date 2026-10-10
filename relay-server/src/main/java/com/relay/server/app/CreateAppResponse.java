package com.relay.server.app;

import java.util.UUID;

/**
 * Response when creating an app.
 * Contains the raw API key (shown only once).
 */
public record CreateAppResponse(
        UUID id,
        String name,
        String apiKey,  // Raw key, shown once
        String apiKeyPrefix
) {}