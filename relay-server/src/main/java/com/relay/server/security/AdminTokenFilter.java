// File: relay-server/src/main/java/com/relay/server/security/AdminTokenFilter.java
package com.relay.server.security;

import com.relay.server.common.UnauthorizedException;
import com.relay.server.config.RelayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects {@code POST /api/v1/apps} with the admin token ({@code RELAY_ADMIN_TOKEN}),
 * sent as {@code Authorization: Bearer <admin_token>}. Compared in constant time.
 * Not a Spring bean; see {@link ApiKeyAuthFilter}.
 */
public class AdminTokenFilter extends OncePerRequestFilter {

    private final RelayProperties properties;
    private final HandlerExceptionResolver exceptionResolver;

    public AdminTokenFilter(RelayProperties properties, HandlerExceptionResolver exceptionResolver) {
        this.properties = properties;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BearerTokens.isAppCreation(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean valid = BearerTokens.extract(request).map(this::matchesAdminToken).orElse(false);
        if (!valid) {
            exceptionResolver.resolveException(request, response, null,
                    new UnauthorizedException("Missing or invalid admin token"));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matchesAdminToken(String presented) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.getAdminToken().getBytes(StandardCharsets.UTF_8));
    }
}
