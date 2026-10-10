// File: relay-server/src/main/java/com/relay/server/event/EventController.java
package com.relay.server.event;

import com.relay.server.security.CurrentApp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Publish and read events. Requires an API key; the app id always comes from {@link CurrentApp}.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    static final String REPLAY_HEADER = "Idempotent-Replayed";

    private final EventApiService eventApiService;
    private final CurrentApp currentApp;

    /** Publishes an event; 202 because delivery happens asynchronously. */
    @PostMapping
    public ResponseEntity<PublishEventResponse> publish(
            @Valid @RequestBody PublishEventRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PublishEventResponse body = eventApiService.publish(
                currentApp.id(), request.type(), request.payload(), idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.ACCEPTED);
        if (body.replayed()) {
            response.header(REPLAY_HEADER, "true");
        }
        return response.body(body);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) {
        return eventApiService.get(currentApp.id(), id);
    }
}
