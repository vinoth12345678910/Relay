// File: relay-server/src/main/java/com/relay/server/delivery/ReplayResponse.java
package com.relay.server.delivery;

/** Result of a replay request: the delivery as it is now, plus a human-readable note. */
public record ReplayResponse(DeliveryDetail delivery, String message) {}
