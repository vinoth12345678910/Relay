// File: relay-server/src/main/java/com/relay/server/event/FanOutService.java
package com.relay.server.event;

import com.relay.server.delivery.Delivery;
import com.relay.server.delivery.DeliveryRepository;
import com.relay.server.delivery.DeliveryStatus;
import com.relay.server.endpoint.EndpointRepository;
import com.relay.server.endpoint.EndpointStatus;
import com.relay.server.endpoint.EventTypeMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Creates one PENDING delivery per ACTIVE or PAUSED endpoint whose filter matches the event.
 * PAUSED endpoints (manual pause or circuit breaker) keep a queue of waiting deliveries, which the
 * claim SQL skips until the endpoint is resumed. DISABLED (soft-deleted) endpoints get nothing.
 * Joins the caller's transaction: it must run in the same transaction that saves the event.
 */
@Service
@RequiredArgsConstructor
public class FanOutService {

    private final EndpointRepository endpointRepository;
    private final DeliveryRepository deliveryRepository;
    private final Clock clock;

    /** Saves the deliveries and returns how many were created. */
    public long fanOut(Event event) {
        Instant now = clock.instant();
        List<Delivery> deliveries = endpointRepository
                .findByAppIdAndStatusIn(event.getAppId(), List.of(EndpointStatus.ACTIVE, EndpointStatus.PAUSED)).stream()
                .filter(e -> EventTypeMatcher.matches(e.getEventTypeFilter(), event.getType()))
                .map(e -> Delivery.builder()
                        .eventId(event.getId())
                        .endpointId(e.getId())
                        .status(DeliveryStatus.PENDING)
                        .attemptCount(0)
                        .nextAttemptAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        deliveryRepository.saveAll(deliveries);
        return deliveries.size();
    }
}
