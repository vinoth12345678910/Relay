// File: relay-server/src/main/java/com/relay/server/event/EventRepository.java
package com.relay.server.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findByIdAndAppId(UUID id, UUID appId);
    Optional<Event> findByAppIdAndIdempotencyKey(UUID appId, String idempotencyKey);
}
