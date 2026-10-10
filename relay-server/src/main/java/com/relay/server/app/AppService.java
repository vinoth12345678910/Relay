// File: relay-server/src/main/java/com/relay/server/app/AppService.java
package com.relay.server.app;

import com.relay.server.common.ValidationException;
import com.relay.server.security.KeyHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * App (tenant) management and API key authentication.
 * Entities stay inside this class; callers only see DTOs.
 */
@Service
@RequiredArgsConstructor
public class AppService {

    private static final String KEY_PREFIX = "rl_live_";
    private static final int KEY_BYTES = 32;
    private static final int DISPLAY_PREFIX_LENGTH = 12;

    private final AppRepository appRepository;
    private final KeyHasher keyHasher;
    private final Random random;
    private final Clock clock;

    /**
     * Creates an app and returns its API key. The raw key is only available in this response.
     * The admin token is verified earlier by the admin filter.
     */
    @Transactional
    public CreateAppResponse createApp(CreateAppRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ValidationException("name", "name is required");
        }
        String rawKey = generateApiKey();
        App saved = appRepository.save(App.builder()
                .name(request.name().trim())
                .apiKeyHash(keyHasher.hash(rawKey))
                .apiKeyPrefix(rawKey.substring(0, DISPLAY_PREFIX_LENGTH))
                .createdAt(clock.instant())
                .build());
        return new CreateAppResponse(saved.getId(), saved.getName(), rawKey, saved.getApiKeyPrefix());
    }

    /** Looks up an app by id. */
    @Transactional(readOnly = true)
    public Optional<AppResponse> getApp(UUID appId) {
        return appRepository.findById(appId).map(this::toResponse);
    }

    /** Resolves a raw API key to its app, or empty if the key is unknown. */
    @Transactional(readOnly = true)
    public Optional<AppResponse> authenticate(String rawKey) {
        return appRepository.findByApiKeyHash(keyHasher.hash(rawKey)).map(this::toResponse);
    }

    private String generateApiKey() {
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AppResponse toResponse(App app) {
        return new AppResponse(app.getId(), app.getName(), app.getCreatedAt());
    }
}
