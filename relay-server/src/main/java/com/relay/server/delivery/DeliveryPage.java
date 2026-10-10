// File: relay-server/src/main/java/com/relay/server/delivery/DeliveryPage.java
package com.relay.server.delivery;

import java.util.List;

/** One page of deliveries; {@code nextCursor} is null on the last page. */
public record DeliveryPage(List<DeliveryDetail> items, String nextCursor) {}
