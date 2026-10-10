// File: relay-server/src/main/java/com/relay/server/endpoint/EndpointService.java
package com.relay.server.endpoint;

import com.relay.server.common.NotFoundException;
import com.relay.server.security.SecretCipher;
import com.relay.server.security.SsrfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Endpoint management. Every lookup goes through {@code findByIdAndAppId} or
 * {@code findByAppId}, so one app can never see or change another app's endpoints.
 * DISABLED endpoints are soft-deleted and treated as not found.
 */
@Service
@RequiredArgsConstructor
public class EndpointService {

    private static final int SECRET_BYTES = 32;
    private static final String SECRET_PREFIX = "whsec_";
    private static final String DEFAULT_FILTER = "*";
    private static final String DEFAULT_PAUSE_REASON = "Paused manually";

    private final EndpointRepository endpointRepository;
    private final SecretCipher secretCipher;
    private final SsrfGuard ssrfGuard;
    private final Random random;
    private final Clock clock;

    /** Registers an endpoint with a fresh signing secret, returned once in plaintext. */
    @Transactional
    public CreateEndpointResponse createEndpoint(UUID appId, CreateEndpointRequest request) {
        ssrfGuard.validate(request.url());
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        var now = clock.instant();
        Endpoint saved = endpointRepository.save(Endpoint.builder()
                .appId(appId)
                .url(request.url().trim())
                .description(request.description())
                .eventTypeFilter(orDefault(request.eventTypeFilter()))
                .signingSecret(secretCipher.encrypt(secret))
                .status(EndpointStatus.ACTIVE)
                .consecutiveFailures(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return CreateEndpointResponse.of(toResponse(saved), SECRET_PREFIX + encode(secret));
    }

    /** Lists the app's endpoints, excluding soft-deleted ones. */
    @Transactional(readOnly = true)
    public List<EndpointResponse> listEndpoints(UUID appId) {
        return endpointRepository.findByAppId(appId).stream()
                .filter(e -> e.getStatus() != EndpointStatus.DISABLED)
                .map(this::toResponse)
                .toList();
    }

    /** Fetches one endpoint or throws 404. */
    @Transactional(readOnly = true)
    public EndpointResponse getEndpoint(UUID appId, UUID endpointId) {
        return toResponse(find(appId, endpointId));
    }

    /** Applies a partial update. */
    @Transactional
    public EndpointResponse updateEndpoint(UUID appId, UUID endpointId, UpdateEndpointRequest request) {
        Endpoint endpoint = find(appId, endpointId);
        if (request.url() != null) {
            ssrfGuard.validate(request.url());
            endpoint.setUrl(request.url().trim());
        }
        if (request.description() != null) {
            endpoint.setDescription(request.description());
        }
        if (request.eventTypeFilter() != null) {
            endpoint.setEventTypeFilter(request.eventTypeFilter());
        }
        return save(endpoint);
    }

    /** Soft-deletes the endpoint (status DISABLED). */
    @Transactional
    public void deleteEndpoint(UUID appId, UUID endpointId) {
        Endpoint endpoint = find(appId, endpointId);
        endpoint.setStatus(EndpointStatus.DISABLED);
        save(endpoint);
    }

    /** Pauses delivery to the endpoint. Idempotent. */
    @Transactional
    public EndpointResponse pauseEndpoint(UUID appId, UUID endpointId, String reason) {
        Endpoint endpoint = find(appId, endpointId);
        endpoint.setStatus(EndpointStatus.PAUSED);
        endpoint.setPausedReason(reason == null || reason.isBlank() ? DEFAULT_PAUSE_REASON : reason);
        return save(endpoint);
    }

    /** Resumes delivery and resets the failure counter. Idempotent. */
    @Transactional
    public EndpointResponse resumeEndpoint(UUID appId, UUID endpointId) {
        Endpoint endpoint = find(appId, endpointId);
        endpoint.setStatus(EndpointStatus.ACTIVE);
        endpoint.setPausedReason(null);
        endpoint.setConsecutiveFailures(0);
        return save(endpoint);
    }

    private Endpoint find(UUID appId, UUID endpointId) {
        return endpointRepository.findByIdAndAppId(endpointId, appId)
                .filter(e -> e.getStatus() != EndpointStatus.DISABLED)
                .orElseThrow(() -> new NotFoundException("Endpoint not found"));
    }

    private EndpointResponse save(Endpoint endpoint) {
        endpoint.setUpdatedAt(clock.instant());
        return toResponse(endpointRepository.save(endpoint));
    }

    private String encode(byte[] secret) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private String orDefault(String filter) {
        return filter == null || filter.isBlank() ? DEFAULT_FILTER : filter;
    }

    private EndpointResponse toResponse(Endpoint e) {
        return new EndpointResponse(e.getId(), e.getUrl(), e.getDescription(), e.getEventTypeFilter(),
                e.getStatus(), e.getConsecutiveFailures(), e.getPausedReason(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
