// File: relay-server/src/test/java/com/relay/server/unit/EventTypeMatcherTest.java
package com.relay.server.unit;

import com.relay.server.endpoint.EventTypeMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypeMatcherTest {

    @ParameterizedTest(name = "filter=''{0}'' type=''{1}'' -> {2}")
    @CsvSource({
            "order.created, order.created, true",
            "order.created, order.updated, false",
            "order.created, order.created.v2, false",
            "order.*, order.created, true",
            "order.*, order.created.v2, true",
            "order.*, order.refund.new, true",
            "order.*, user.created, false",
            "order.*, orders.created, false",
            "order.*, order, false",
            "*, order.created, true",
            "*, anything, true",
            "'  order.*  ', order.created, true",
            "'  order.created ', order.created, true",
            "'  *  ', user.signup, true",
            "'', order.created, true",
            "'   ', order.created, true"
    })
    void matchesFilterAgainstType(String filter, String type, boolean expected) {
        assertThat(EventTypeMatcher.matches(filter, type)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankOrNullFilterMatchesEverything(String filter) {
        assertThat(EventTypeMatcher.matches(filter, "order.created")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankOrNullEventTypeNeverMatches(String type) {
        assertThat(EventTypeMatcher.matches("*", type)).isFalse();
        assertThat(EventTypeMatcher.matches(null, type)).isFalse();
        assertThat(EventTypeMatcher.matches("order.*", type)).isFalse();
    }
}
