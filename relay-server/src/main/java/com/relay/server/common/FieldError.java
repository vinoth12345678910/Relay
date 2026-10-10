// File: relay-server/src/main/java/com/relay/server/common/FieldError.java
package com.relay.server.common;

/**
 * A single field-level validation problem, returned inside {@link ApiError#fieldErrors()}.
 */
public record FieldError(String field, String message) {}
