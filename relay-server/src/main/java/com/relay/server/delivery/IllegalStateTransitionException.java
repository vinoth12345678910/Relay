// File: relay-server/src/main/java/com/relay/server/delivery/IllegalStateTransitionException.java
package com.relay.server.delivery;

/**
 * A delivery status change that the state machine does not allow. Indicates a bug, not bad input.
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(DeliveryStatus from, DeliveryStatus to) {
        super("Illegal delivery transition " + from + " -> " + to);
    }
}
