// File: relay-server/src/main/java/com/relay/server/event/EventApiService.java
package com.relay.server.event;

import com.relay.server.delivery.Delivery;
import com.relay.server.delivery.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * API-facing wrapper around {@link EventService} that maps entities to DTOs, so controllers
 * never see an entity. Publishing logic itself stays in {@link EventService}.
 */
@Service
@RequiredArgsConstructor
public class EventApiService {

    private final EventService eventService;
    private final DeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    /** Publishes (or replays) an event and returns its public view. */
    public PublishEventResponse publish(UUID appId, String type, JsonNode payload, String idempotencyKey) {
        PublishResult result = eventService.publish(appId, type, payload, idempotencyKey);
        Event event = result.event();
        return new PublishEventResponse(event.getId(), event.getType(), event.getCreatedAt(),
                result.deliveryCount(), result.replayed());
    }

    /** Returns the calling app's event with its deliveries; 404 for anyone else's event. */
    public EventResponse get(UUID appId, UUID eventId) {
        Event event = eventService.get(appId, eventId);
        List<DeliverySummary> deliveries = deliveryRepository.findByEventId(event.getId()).stream()
                .map(this::toSummary)
                .toList();
        return new EventResponse(event.getId(), event.getType(), objectMapper.readTree(event.getPayload()),
                event.getCreatedAt(), deliveries);
    }

    private DeliverySummary toSummary(Delivery d) {
        return new DeliverySummary(d.getId(), d.getEndpointId(), d.getStatus(), d.getAttemptCount(),
                d.getNextAttemptAt(), d.getLastStatusCode());
    }
}
