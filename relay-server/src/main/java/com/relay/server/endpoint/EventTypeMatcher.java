// File: relay-server/src/main/java/com/relay/server/endpoint/EventTypeMatcher.java
package com.relay.server.endpoint;

/**
 * Matches an event type against an endpoint's event type filter.
 */
public final class EventTypeMatcher {

    private EventTypeMatcher() {}

    /**
     * Rules: blank eventType never matches; blank filter or {@code *} matches everything;
     * {@code x.*} matches types starting with {@code x.} (the dot is kept, so
     * {@code order.*} does not match {@code orders.created} or {@code order}); otherwise exact match.
     */
    public static boolean matches(String filter, String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return false;
        }
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String f = filter.trim();
        if (f.equals("*")) {
            return true;
        }
        if (f.endsWith(".*")) {
            return eventType.startsWith(f.substring(0, f.length() - 1));
        }
        return f.equals(eventType);
    }
}
