// File: relay-server/src/main/java/com/relay/server/common/ConflictException.java
package com.relay.server.common;

/**
 * The request conflicts with current resource state. Maps to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
