// File: relay-server/src/main/java/com/relay/server/delivery/DeliveryController.java
package com.relay.server.delivery;

import com.relay.server.security.CurrentApp;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Delivery inspection and replay. Requires an API key; the app id always comes from {@link CurrentApp}.
 */
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final CurrentApp currentApp;

    @GetMapping
    public DeliveryPage list(@RequestParam(required = false) String status,
                             @RequestParam(required = false) UUID endpointId,
                             @RequestParam(required = false) String cursor,
                             @RequestParam(required = false) Integer limit) {
        return deliveryService.list(currentApp.id(), status, endpointId, cursor, limit);
    }

    @GetMapping("/{id}")
    public DeliveryDetail get(@PathVariable UUID id) {
        return deliveryService.get(currentApp.id(), id);
    }

    @GetMapping("/{id}/attempts")
    public List<AttemptView> attempts(@PathVariable UUID id) {
        return deliveryService.attempts(currentApp.id(), id);
    }

    @PostMapping("/{id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReplayResponse replay(@PathVariable UUID id) {
        return deliveryService.replay(currentApp.id(), id);
    }
}
