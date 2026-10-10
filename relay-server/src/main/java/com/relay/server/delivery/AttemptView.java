// File: relay-server/src/main/java/com/relay/server/delivery/AttemptView.java
package com.relay.server.delivery;

import java.time.Instant;

/** Public view of one HTTP attempt. */
public record AttemptView(int attemptNumber, Instant startedAt, long durationMs,
                          Integer statusCode, String error, String responseSnippet) {}
