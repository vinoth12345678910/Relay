// File: relay-server/src/main/java/com/relay/server/app/AppController.java
package com.relay.server.app;

import com.relay.server.common.NotFoundException;
import com.relay.server.security.CurrentApp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * App endpoints. Creation is guarded by the admin token filter; reads by the API key filter.
 */
@RestController
@RequestMapping("/api/v1/apps")
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;
    private final CurrentApp currentApp;

    /** Creates an app. Returns the raw API key once. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAppResponse create(@Valid @RequestBody CreateAppRequest request) {
        return appService.createApp(request);
    }

    /** Returns the caller's own app; any other id is a 404 so app ids cannot be probed. */
    @GetMapping("/{id}")
    public AppResponse get(@PathVariable UUID id) {
        if (!id.equals(currentApp.id())) {
            throw new NotFoundException("App not found");
        }
        return appService.getApp(id).orElseThrow(() -> new NotFoundException("App not found"));
    }
}
