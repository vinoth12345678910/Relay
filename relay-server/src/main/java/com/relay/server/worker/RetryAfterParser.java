// File: relay-server/src/main/java/com/relay/server/worker/RetryAfterParser.java
package com.relay.server.worker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Parses a {@code Retry-After} header (delta-seconds or HTTP-date) into a bounded wait. Pure and
 * never throws: a missing, malformed, zero, negative or already-past value gives empty, and
 * anything above {@code max} is capped so a receiver cannot park a delivery forever.
 */
public final class RetryAfterParser {

    private static final int MAX_DIGITS = 9;   // longer than this is certainly above any sane cap

    private RetryAfterParser() {}

    public static Optional<Duration> parse(String header, Clock clock, Duration max) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String value = header.trim();
        Duration wait;
        try {
            if (isAsciiDigits(value)) {
                if (value.length() > MAX_DIGITS) {
                    return Optional.of(max);
                }
                wait = Duration.ofSeconds(Long.parseLong(value));
            } else {
                ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                wait = Duration.between(clock.instant(), when.toInstant());
            }
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (wait.isZero() || wait.isNegative()) {
            return Optional.empty();
        }
        return Optional.of(wait.compareTo(max) > 0 ? max : wait);
    }

    private static boolean isAsciiDigits(String s) {
        return s.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
