// File: relay-server/src/main/java/com/relay/server/common/ApiError.java
package com.relay.server.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every failing API call.
 *
 * @param timestamp   when the error was produced (from the injected Clock)
 * @param status      HTTP status code
 * @param error       HTTP reason phrase
 * @param code        stable machine-readable code, e.g. NOT_FOUND
 * @param message     human-readable message; never contains keys or secrets
 * @param path        request path
 * @param fieldErrors field-level problems; omitted when empty
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> fieldErrors
) {}
