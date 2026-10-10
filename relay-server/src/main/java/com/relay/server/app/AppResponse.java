// File: relay-server/src/main/java/com/relay/server/app/AppResponse.java
package com.relay.server.app;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of an app. Omits the key hash and prefix.
 */
public record AppResponse(UUID id, String name, Instant createdAt) {}
