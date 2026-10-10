// File: relay-server/src/main/java/com/relay/server/security/BearerTokens.java
package com.relay.server.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Helpers shared by the auth filters.
 */
final class BearerTokens {

    private static final String PREFIX = "Bearer ";

    /** Routes that need an API key (everything except app creation). */
    static final String API_PREFIX = "/api/v1/";
    /** The one admin-protected route. */
    static final String APPS_PATH = "/api/v1/apps";

    private BearerTokens() {}

    /** Extracts the token from {@code Authorization: Bearer <token>}, if present and non-blank. */
    static Optional<String> extract(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /** Request path relative to the servlet context path, without a trailing slash. */
    static String path(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /** True for {@code POST /api/v1/apps}, the only admin-protected route. */
    static boolean isAppCreation(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && APPS_PATH.equals(path(request));
    }
}
