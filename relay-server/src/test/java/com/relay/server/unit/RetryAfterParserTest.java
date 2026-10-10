// File: relay-server/src/test/java/com/relay/server/unit/RetryAfterParserTest.java
package com.relay.server.unit;

import com.relay.server.worker.RetryAfterParser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterParserTest {

    // Wed 2026-10-21 07:00:00 UTC
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T07:00:00Z"), ZoneOffset.UTC);
    private final Duration max = Duration.ofHours(1);

    @Test
    void seconds() {
        assertThat(RetryAfterParser.parse("120", clock, max)).contains(Duration.ofSeconds(120));
        assertThat(RetryAfterParser.parse("  7  ", clock, max)).contains(Duration.ofSeconds(7));
    }

    @Test
    void httpDate() {
        assertThat(RetryAfterParser.parse("Wed, 21 Oct 2026 07:05:00 GMT", clock, max)).contains(Duration.ofMinutes(5));
    }

    @Test
    void cappedAtMax() {
        assertThat(RetryAfterParser.parse("3600", clock, max)).contains(max);
        assertThat(RetryAfterParser.parse("3601", clock, max)).contains(max);
        assertThat(RetryAfterParser.parse("999999", clock, max)).contains(max);
        assertThat(RetryAfterParser.parse("99999999999999", clock, max)).contains(max);
        assertThat(RetryAfterParser.parse("Mon, 21 Oct 2030 07:00:00 GMT", clock, max)).contains(max);
    }

    @Test
    void ignoredWhenUseless() {
        assertThat(RetryAfterParser.parse(null, clock, max)).isEmpty();
        assertThat(RetryAfterParser.parse("", clock, max)).isEmpty();
        assertThat(RetryAfterParser.parse("  ", clock, max)).isEmpty();
        assertThat(RetryAfterParser.parse("soon", clock, max)).isEmpty();
        assertThat(RetryAfterParser.parse("-5", clock, max)).isEmpty();
        assertThat(RetryAfterParser.parse("1.5", clock, max)).isEmpty();
        assertThat(RetryAfterParser.parse("0", clock, max)).isEmpty();
        assertThat(RetryAfterParser.parse("Wed, 21 Oct 2026 06:00:00 GMT", clock, max)).as("date in the past").isEmpty();
        assertThat(RetryAfterParser.parse("Wed, 21 Oct 2026 07:00:00 GMT", clock, max)).as("date is now").isEmpty();
    }

    @Test
    void aDateWhoseWeekdayDoesNotMatchIsTreatedAsMalformed() {
        // 21 Oct 2030 is a Monday, not a Wednesday: the strict RFC 1123 parser rejects it, so it is ignored.
        assertThat(RetryAfterParser.parse("Wed, 21 Oct 2030 07:00:00 GMT", clock, max)).isEmpty();
    }
}
