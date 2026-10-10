package com.relay.server.app;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to create an app.
 * Only the name is needed; we auto-generate the API key.
 */
public record CreateAppRequest(
        @NotBlank(message = "name is required")
        String name
) {}