package com.relay.server.endpoint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Endpoint entity: a webhook destination registered by an app.
 */
@Entity
@Table(name = "endpoints")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Endpoint {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false, columnDefinition = "UUID")
    private UUID appId;

    @Column(nullable = false)
    private String url;

    @Column
    private String description;

    @Column(nullable = false)
    private String eventTypeFilter;

    @Column(nullable = false)
    private byte[] signingSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EndpointStatus status;

    @Column(nullable = false)
    private Integer consecutiveFailures;

    @Column
    private String pausedReason;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (consecutiveFailures == null) {
            consecutiveFailures = 0;
        }
        if (status == null) {
            status = EndpointStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}