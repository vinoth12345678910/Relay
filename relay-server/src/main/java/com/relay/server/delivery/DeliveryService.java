// File: relay-server/src/main/java/com/relay/server/delivery/DeliveryService.java
package com.relay.server.delivery;

import com.relay.server.common.ConflictException;
import com.relay.server.common.NotFoundException;
import com.relay.server.common.ValidationException;
import com.relay.server.common.pagination.Cursor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read and replay API for deliveries. A delivery belongs to an app through its event, so every query
 * joins {@code events} and filters on {@code e.app_id}: that join is the tenant check, and another
 * app's delivery is indistinguishable from one that does not exist (404).
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private static final String SELECT_DELIVERY = """
            SELECT d.id, d.event_id, d.endpoint_id, d.status, d.attempt_count, d.attempt_base,
                   d.next_attempt_at, d.last_status_code, d.created_at, d.updated_at
            FROM deliveries d
            JOIN events e ON e.id = d.event_id
            """;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final DeliveryStateMachine stateMachine;

    /** Locked, ownership-checked snapshot of a delivery taken at the start of a replay. */
    private record ReplayTarget(DeliveryStatus status, int attemptCount, UUID endpointId) {}

    // ---------- list ----------

    /** Newest first, keyset-paginated. {@code limit} defaults to 50 (max 200). */
    public DeliveryPage list(UUID appId, String status, UUID endpointId, String cursor, Integer limit) {
        int pageSize = resolveLimit(limit);
        DeliveryStatus statusFilter = parseStatus(status);
        Cursor after = cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor);

        StringBuilder sql = new StringBuilder(SELECT_DELIVERY).append(" WHERE e.app_id = :appId");
        if (statusFilter != null) {
            sql.append(" AND d.status = :status");
        }
        if (endpointId != null) {
            sql.append(" AND d.endpoint_id = :endpointId");
        }
        if (after != null) {
            sql.append(" AND (d.created_at, d.id) < (:cursorTs, :cursorId)");
        }
        sql.append(" ORDER BY d.created_at DESC, d.id DESC LIMIT :limit");

        var query = jdbc.sql(sql.toString())
                .param("appId", appId)
                .param("limit", pageSize + 1);   // one extra row tells us whether a next page exists
        if (statusFilter != null) {
            query = query.param("status", statusFilter.name());
        }
        if (endpointId != null) {
            query = query.param("endpointId", endpointId);
        }
        if (after != null) {
            query = query.param("cursorTs", utc(after.createdAt())).param("cursorId", after.id());
        }

        List<DeliveryDetail> rows = new ArrayList<>(query.query((rs, i) -> map(rs)).list());
        String next = null;
        if (rows.size() > pageSize) {
            rows.remove(rows.size() - 1);
            DeliveryDetail last = rows.get(rows.size() - 1);
            next = new Cursor(last.createdAt(), last.id()).encode();
        }
        return new DeliveryPage(rows, next);
    }

    // ---------- detail ----------

    /** One delivery of the calling app, or 404. */
    public DeliveryDetail get(UUID appId, UUID deliveryId) {
        return jdbc.sql(SELECT_DELIVERY + " WHERE d.id = :id AND e.app_id = :appId")
                .param("id", deliveryId)
                .param("appId", appId)
                .query((rs, i) -> map(rs)).optional()
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
    }

    // ---------- attempts ----------

    /** Every HTTP attempt of the delivery, oldest first. */
    public List<AttemptView> attempts(UUID appId, UUID deliveryId) {
        get(appId, deliveryId);   // 404 for another app's delivery before touching attempts
        return jdbc.sql("""
                        SELECT attempt_number, started_at, duration_ms, status_code, error, response_snippet
                        FROM delivery_attempts WHERE delivery_id = :id ORDER BY attempt_number
                        """)
                .param("id", deliveryId)
                .query((rs, i) -> new AttemptView(
                        rs.getInt("attempt_number"),
                        rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("duration_ms"),
                        (Integer) rs.getObject("status_code"),
                        rs.getString("error"),
                        rs.getString("response_snippet")))
                .list();
    }

    // ---------- replay ----------

    /**
     * Puts a DEAD or SUCCEEDED delivery back to PENDING with a fresh retry budget. The row is locked
     * ({@code FOR UPDATE}) together with the ownership check, so two simultaneous replays serialize:
     * the second sees PENDING and gets a 409. {@code attempt_count} keeps growing so attempt rows stay unique.
     */
    @Transactional
    public ReplayResponse replay(UUID appId, UUID deliveryId) {
        ReplayTarget target = lockForReplay(appId, deliveryId);
        if (target.status() != DeliveryStatus.DEAD && target.status() != DeliveryStatus.SUCCEEDED) {
            throw new ConflictException("Only DEAD or SUCCEEDED deliveries can be replayed (status is " + target.status() + ")");
        }
        stateMachine.transition(target.status(), DeliveryStatus.PENDING);

        OffsetDateTime now = utc(Instant.now(clock));
        jdbc.sql("""
                        UPDATE deliveries
                        SET status = 'PENDING', attempt_base = :base, next_attempt_at = :now,
                            locked_until = NULL, updated_at = :now
                        WHERE id = :id
                        """)
                .param("base", target.attemptCount())   // the fresh budget starts here
                .param("now", now)
                .param("id", deliveryId)
                .update();

        return new ReplayResponse(get(appId, deliveryId), replayMessage(target.endpointId()));
    }

    private ReplayTarget lockForReplay(UUID appId, UUID deliveryId) {
        return jdbc.sql("""
                        SELECT d.status, d.attempt_count, d.endpoint_id
                        FROM deliveries d JOIN events e ON e.id = d.event_id
                        WHERE d.id = :id AND e.app_id = :appId
                        FOR UPDATE OF d
                        """)
                .param("id", deliveryId)
                .param("appId", appId)
                .query((rs, i) -> new ReplayTarget(
                        DeliveryStatus.valueOf(rs.getString("status")),
                        rs.getInt("attempt_count"),
                        rs.getObject("endpoint_id", UUID.class)))
                .optional()
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
    }

    private String replayMessage(UUID endpointId) {
        String endpointStatus = jdbc.sql("SELECT status FROM endpoints WHERE id = :id")
                .param("id", endpointId).query(String.class).optional().orElse("");
        return switch (endpointStatus) {
            case "PAUSED" -> "Replay queued, but the endpoint is PAUSED, so it will be sent after the endpoint is resumed.";
            case "DISABLED" -> "Replay queued, but the endpoint was deleted, so it will not be sent.";
            default -> "Replay queued.";
        };
    }

    // ---------- helpers ----------

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ValidationException("limit", "limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private DeliveryStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DeliveryStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("status", "status must be one of PENDING, IN_FLIGHT, SUCCEEDED, DEAD");
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static DeliveryDetail map(ResultSet rs) throws SQLException {
        return new DeliveryDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("endpoint_id", UUID.class),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                rs.getInt("attempt_base"),
                rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
                (Integer) rs.getObject("last_status_code"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
