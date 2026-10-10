// File: relay-server/src/main/java/com/relay/server/common/PayloadTooLargeException.java
package com.relay.server.common;

/**
 * The request payload exceeds the allowed size. Maps to HTTP 413.
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
