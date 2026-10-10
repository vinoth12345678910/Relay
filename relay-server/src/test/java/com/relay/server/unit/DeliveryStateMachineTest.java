// File: relay-server/src/test/java/com/relay/server/unit/DeliveryStateMachineTest.java
package com.relay.server.unit;

import com.relay.server.delivery.DeliveryStateMachine;
import com.relay.server.delivery.DeliveryStatus;
import com.relay.server.delivery.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.relay.server.delivery.DeliveryStatus.DEAD;
import static com.relay.server.delivery.DeliveryStatus.IN_FLIGHT;
import static com.relay.server.delivery.DeliveryStatus.PENDING;
import static com.relay.server.delivery.DeliveryStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryStateMachineTest {

    private final DeliveryStateMachine machine = new DeliveryStateMachine();

    /** The full 4x4 matrix: every (from, to) pair, legal or not. */
    @ParameterizedTest(name = "{0} -> {1} legal={2}")
    @CsvSource({
            "PENDING,   PENDING,   false",
            "PENDING,   IN_FLIGHT, true",
            "PENDING,   SUCCEEDED, false",
            "PENDING,   DEAD,      false",
            "IN_FLIGHT, PENDING,   true",
            "IN_FLIGHT, IN_FLIGHT, true",
            "IN_FLIGHT, SUCCEEDED, true",
            "IN_FLIGHT, DEAD,      true",
            "SUCCEEDED, PENDING,   true",
            "SUCCEEDED, IN_FLIGHT, false",
            "SUCCEEDED, SUCCEEDED, false",
            "SUCCEEDED, DEAD,      false",
            "DEAD,      PENDING,   true",
            "DEAD,      IN_FLIGHT, false",
            "DEAD,      SUCCEEDED, false",
            "DEAD,      DEAD,      false"
    })
    void matrix(DeliveryStatus from, DeliveryStatus to, boolean legal) {
        assertThat(machine.isLegal(from, to)).isEqualTo(legal);
        if (legal) {
            assertThat(machine.transition(from, to)).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> machine.transition(from, to))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessage("Illegal delivery transition " + from + " -> " + to);
        }
    }

    @Test
    void matrixCoversEveryPair() {
        int legal = 0;
        for (DeliveryStatus from : DeliveryStatus.values()) {
            for (DeliveryStatus to : DeliveryStatus.values()) {
                legal += machine.isLegal(from, to) ? 1 : 0;
            }
        }
        assertThat(DeliveryStatus.values()).hasSize(4);
        assertThat(legal).isEqualTo(7);
        assertThat(machine.isLegal(PENDING, IN_FLIGHT) && machine.isLegal(IN_FLIGHT, SUCCEEDED)
                && machine.isLegal(IN_FLIGHT, DEAD) && machine.isLegal(DEAD, PENDING)).isTrue();
    }
}
