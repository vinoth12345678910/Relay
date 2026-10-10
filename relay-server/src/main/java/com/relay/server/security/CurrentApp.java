// File: relay-server/src/main/java/com/relay/server/security/CurrentApp.java
package com.relay.server.security;

import com.relay.server.common.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

/**
 * Request-scoped holder for the authenticated app. Populated by {@link ApiKeyAuthFilter};
 * controllers inject it and pass {@link #id()} to services so every query is app-scoped.
 */
@Component
@RequestScope
public class CurrentApp {

    private UUID id;
    private String name;

    /** Called by the auth filter after a successful key lookup. */
    public void set(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /** The authenticated app id; throws 401 if the request was not authenticated. */
    public UUID id() {
        if (id == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return id;
    }

    /** The authenticated app name. */
    public String name() {
        return name;
    }
}
