// File: relay-server/src/main/java/com/relay/server/common/pagination/Cursor.java
package com.relay.server.common.pagination;

import com.relay.server.common.ValidationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Keyset pagination bookmark: base64url("createdAt|id"). Clients must treat it as opaque.
 * Paging by "older than this row" (not by offset) means rows inserted meanwhile cannot shift a page.
 */
public record Cursor(Instant createdAt, UUID id) {

    public String encode() {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Throws {@link ValidationException} (field {@code cursor}) if the token is not one we issued. */
    public static Cursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new ValidationException("cursor", "invalid cursor");
        }
    }
}
