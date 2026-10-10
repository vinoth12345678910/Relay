// File: relay-server/src/main/java/com/relay/server/delivery/AttemptRepository.java
package com.relay.server.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    List<DeliveryAttempt> findByDeliveryIdOrderByAttemptNumber(UUID deliveryId);
}
