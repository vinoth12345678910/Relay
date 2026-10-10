// File: relay-server/src/main/java/com/relay/server/common/ValidationException.java
package com.relay.server.common;

import java.util.List;

/**
 * Business-level validation failure with optional field errors. Maps to HTTP 400.
 */
public class ValidationException extends RuntimeException {

    private final transient List<FieldError> fieldErrors;

    public ValidationException(String message, List<FieldError> fieldErrors) {
        super(message);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** Convenience for a single failing field. */
    public ValidationException(String field, String message) {
        this("Validation failed", List.of(new FieldError(field, message)));
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
