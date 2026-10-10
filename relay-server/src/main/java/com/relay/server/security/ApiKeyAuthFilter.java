// File: relay-server/src/main/java/com/relay/server/security/ApiKeyAuthFilter.java
package com.relay.server.security;

import com.relay.server.app.AppResponse;
import com.relay.server.app.AppService;
import com.relay.server.common.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Authenticates {@code /api/v1/**} requests via {@code Authorization: Bearer <api_key>}.
 * On success the app is stored in {@link CurrentApp}. Failures are routed through the
 * {@code HandlerExceptionResolver} so they use the same {@code ApiError} body as controllers.
 * Not a Spring bean: {@link com.relay.server.config.SecurityConfig} builds it, which avoids
 * Boot also registering it as a second servlet filter.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final AppService appService;
    private final CurrentApp currentApp;
    private final HandlerExceptionResolver exceptionResolver;

    public ApiKeyAuthFilter(AppService appService, CurrentApp currentApp,
                            HandlerExceptionResolver exceptionResolver) {
        this.appService = appService;
        this.currentApp = currentApp;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BearerTokens.path(request).startsWith(BearerTokens.API_PREFIX) || BearerTokens.isAppCreation(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        AppResponse app = BearerTokens.extract(request).flatMap(appService::authenticate).orElse(null);
        if (app == null) {
            exceptionResolver.resolveException(request, response, null,
                    new UnauthorizedException("Missing or invalid API key"));
            return;
        }
        currentApp.set(app.id(), app.name());
        chain.doFilter(request, response);
    }
}
