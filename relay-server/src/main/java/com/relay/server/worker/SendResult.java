package com.relay.server.worker;

import java.time.Duration;

public record SendResult(
        boolean success,          // true only for 2xx
        Integer statusCode,       // null if we never got a response
        String error,             // null on success; short text on failure
        long durationMs,
        String responseSnippet,   // first 4 KB of the response body, may be null
        Duration retryAfter       // null unless a 429/503 carried a usable Retry-After (already capped)
) {
    /** Form without Retry-After, so existing callers and tests keep compiling. */
    public SendResult(boolean success, Integer statusCode, String error, long durationMs, String responseSnippet) {
        this(success, statusCode, error, durationMs, responseSnippet, null);
    }
}
