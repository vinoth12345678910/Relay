// File: relay-server/src/main/java/com/relay/server/event/EventService.java
package com.relay.server.event;

import com.relay.server.common.ConflictException;
import com.relay.server.common.NotFoundException;
import com.relay.server.common.PayloadTooLargeException;
import com.relay.server.common.ValidationException;
import com.relay.server.delivery.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Publishes events with idempotency and fans them out to endpoints.
 * Every query is scoped to the calling app.
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[a-z0-9._-]{1,100}$");
    private static final int MAX_PAYLOAD_BYTES = 256 * 1024;

    private final EventRepository eventRepository;
    private final DeliveryRepository deliveryRepository;
    private final FanOutService fanOutService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Stores the event and its deliveries atomically, or replays an earlier identical publish.
     * Deliberately not {@code @Transactional}: the transaction is scoped inside
     * {@link #write} so a duplicate-key failure can be handled after it has rolled back.
     */
    public PublishResult publish(UUID appId, String type, JsonNode payload, String idempotencyKey) {
        validateType(type);
        String json = serialize(payload);
        String hash = sha256Hex(json);
        String key = normalizeKey(idempotencyKey);

        if (key != null) {
            Optional<Event> existing = eventRepository.findByAppIdAndIdempotencyKey(appId, key);
            if (existing.isPresent()) {
                return replay(existing.get(), hash);
            }
        }
        try {
            return transactionTemplate.execute(status -> write(appId, type, json, hash, key));
        } catch (DataIntegrityViolationException e) {
            // Postgres aborts the whole transaction after a failed statement, so nothing can be
            // done inside it. The unique index on (app_id, idempotency_key) is the real guard
            // against concurrent duplicates: the loser rolls back, then re-reads the winner here.
            if (key == null) {
                throw e;
            }
            Event winner = eventRepository.findByAppIdAndIdempotencyKey(appId, key).orElseThrow(() -> e);
            return replay(winner, hash);
        }
    }

    /** Fetches an event of the calling app; other apps' events are a 404. */
    public Event get(UUID appId, UUID eventId) {
        return eventRepository.findByIdAndAppId(eventId, appId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
    }

    private PublishResult write(UUID appId, String type, String json, String hash, String key) {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .appId(appId)
                .type(type)
                .payload(json)
                .idempotencyKey(key)
                .payloadHash(hash)
                .createdAt(clock.instant())
                .build());
        return new PublishResult(event, false, fanOutService.fanOut(event));
    }

    private PublishResult replay(Event existing, String hash) {
        if (!existing.getPayloadHash().equals(hash)) {
            throw new ConflictException("Idempotency key was already used with a different payload");
        }
        return new PublishResult(existing, true, deliveryRepository.countByEventId(existing.getId()));
    }

    private void validateType(String type) {
        if (type == null || !TYPE_PATTERN.matcher(type).matches()) {
            throw new ValidationException("type", "type must match ^[a-z0-9._-]{1,100}$");
        }
    }

    /** Serializes once; this exact string is stored, hashed and later signed. */
    private String serialize(JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            throw new ValidationException("payload", "payload is required");
        }
        String json = objectMapper.writeValueAsString(payload);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException("payload must be at most 256 KB");
        }
        return json;
    }

    private String normalizeKey(String key) {
        return key == null || key.isBlank() ? null : key;
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
