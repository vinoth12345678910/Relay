// File: relay-server/src/main/java/com/relay/server/security/KeyHasher.java
package com.relay.server.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One-way hashing of API keys. Only the hash is stored; the raw key is never persisted or logged.
 *
 * <p>Uses SHA-256 rather than bcrypt: authentication looks the app up by hash
 * ({@code findByApiKeyHash}), which requires a deterministic hash. Bcrypt is salted, so it
 * cannot be used for a lookup. API keys carry 256 bits of randomness, so a slow hash adds nothing.
 */
@Component
public class KeyHasher {

    /** Returns the lowercase hex SHA-256 of the raw key. */
    public String hash(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
