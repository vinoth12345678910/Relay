// File: relay-server/src/main/java/com/relay/server/endpoint/EndpointController.java
package com.relay.server.endpoint;

import com.relay.server.security.CurrentApp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint CRUD. All routes require an API key; the app id always comes from {@link CurrentApp},
 * never from the request.
 */
@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointService endpointService;
    private final CurrentApp currentApp;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEndpointResponse create(@Valid @RequestBody CreateEndpointRequest request) {
        return endpointService.createEndpoint(currentApp.id(), request);
    }

    @GetMapping
    public List<EndpointResponse> list() {
        return endpointService.listEndpoints(currentApp.id());
    }

    @GetMapping("/{id}")
    public EndpointResponse get(@PathVariable UUID id) {
        return endpointService.getEndpoint(currentApp.id(), id);
    }

    @PatchMapping("/{id}")
    public EndpointResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEndpointRequest request) {
        return endpointService.updateEndpoint(currentApp.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        endpointService.deleteEndpoint(currentApp.id(), id);
    }

    @PostMapping("/{id}/pause")
    public EndpointResponse pause(@PathVariable UUID id,
                                  @Valid @RequestBody(required = false) PauseEndpointRequest request) {
        String reason = request == null ? null : request.reason();
        return endpointService.pauseEndpoint(currentApp.id(), id, reason);
    }

    @PostMapping("/{id}/resume")
    public EndpointResponse resume(@PathVariable UUID id) {
        return endpointService.resumeEndpoint(currentApp.id(), id);
    }
}
