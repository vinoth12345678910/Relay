package com.relay.server.endpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {
    List<Endpoint> findByAppIdAndStatus(UUID appId, EndpointStatus status);
    Optional<Endpoint> findByIdAndAppId(UUID id, UUID appId);
    List<Endpoint> findByAppId(UUID appId);
    List<Endpoint> findByAppIdAndStatusIn(UUID appId, Collection<EndpointStatus> statuses);
}