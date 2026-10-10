// File: relay-server/src/main/java/com/relay/server/delivery/Delivery.java
package com.relay.server.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One delivery of an event to an endpoint, with retry bookkeeping.
 * Timestamps are set by the services from the injected Clock.
 */
@Entity
@Table(name = "deliveries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Delivery {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Builder.Default
    @Column(name = "attempt_base", nullable = false)
    private Integer attemptBase = 0;

    @Column(nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    @Column(nullable = false, columnDefinition = "UUID")
    private UUID endpointId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(nullable = false)
    private Integer attemptCount;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant nextAttemptAt;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant lockedUntil;

    @Column
    private Integer lastStatusCode;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
