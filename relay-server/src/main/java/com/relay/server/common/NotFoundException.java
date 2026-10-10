// File: relay-server/src/main/java/com/relay/server/common/NotFoundException.java
package com.relay.server.common;

/**
 * The requested resource does not exist, or belongs to another app. Maps to HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
