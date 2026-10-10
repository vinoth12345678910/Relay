// File: relay-server/src/main/java/com/relay/server/delivery/DeliveryStateMachine.java
package com.relay.server.delivery;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The only authority on legal delivery status changes. Pure: no I/O, no state.
 *
 * <pre>
 * PENDING   -> IN_FLIGHT                       (claimed by a worker)
 * IN_FLIGHT -> SUCCEEDED | PENDING | DEAD      (attempt finished: ok / retry / give up)
 * IN_FLIGHT -> IN_FLIGHT                       (lease expired, re-claimed)
 * SUCCEEDED -> PENDING, DEAD -> PENDING        (manual replay only)
 * </pre>
 *
 * <p>Note: {@code DeliveryClaimer} sets IN_FLIGHT directly in SQL; its WHERE clause (PENDING that is
 * due, or IN_FLIGHT whose lease expired) enforces the PENDING->IN_FLIGHT and IN_FLIGHT->IN_FLIGHT
 * moves. All other changes must call {@link #transition}.
 */
@Component
public class DeliveryStateMachine {

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> LEGAL = new EnumMap<>(DeliveryStatus.class);

    static {
        LEGAL.put(DeliveryStatus.PENDING, EnumSet.of(DeliveryStatus.IN_FLIGHT));
        LEGAL.put(DeliveryStatus.IN_FLIGHT, EnumSet.of(DeliveryStatus.SUCCEEDED, DeliveryStatus.PENDING,
                DeliveryStatus.DEAD, DeliveryStatus.IN_FLIGHT));
        LEGAL.put(DeliveryStatus.SUCCEEDED, EnumSet.of(DeliveryStatus.PENDING));
        LEGAL.put(DeliveryStatus.DEAD, EnumSet.of(DeliveryStatus.PENDING));
    }

    /** Whether {@code from -> to} is allowed. */
    public boolean isLegal(DeliveryStatus from, DeliveryStatus to) {
        return LEGAL.get(from).contains(to);
    }

    /** Returns {@code to} if the change is legal, otherwise throws. */
    public DeliveryStatus transition(DeliveryStatus from, DeliveryStatus to) {
        if (!isLegal(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
        return to;
    }
}
