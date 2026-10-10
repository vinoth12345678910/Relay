// File: relay-server/src/main/java/com/relay/server/delivery/DeliveryRepository.java
package com.relay.server.delivery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    List<Delivery> findByEventId(UUID eventId);
    long countByEventId(UUID eventId);

    /** Loads the delivery with {@code SELECT ... FOR UPDATE}; must be called inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findForUpdate(@Param("id") UUID id);
}
