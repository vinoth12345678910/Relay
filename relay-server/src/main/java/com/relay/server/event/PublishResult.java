// File: relay-server/src/main/java/com/relay/server/event/PublishResult.java
package com.relay.server.event;

/**
 * Outcome of publishing an event.
 *
 * @param event         the stored event (new, or the original on replay)
 * @param replayed      true if an earlier publish with the same idempotency key was returned
 * @param deliveryCount number of deliveries fanned out for the event
 */
public record PublishResult(Event event, boolean replayed, long deliveryCount) {}
