// File: relay-server/src/main/java/com/relay/server/common/UnauthorizedException.java
package com.relay.server.common;

/**
 * Credentials are missing or invalid. Maps to HTTP 401.
 * The message must never include the credential that was presented.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
