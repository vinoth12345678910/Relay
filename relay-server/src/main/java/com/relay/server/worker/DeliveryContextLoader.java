// File: relay-server/src/main/java/com/relay/server/worker/DeliveryContextLoader.java
package com.relay.server.worker;

import com.relay.server.endpoint.Endpoint;
import com.relay.server.endpoint.EndpointRepository;
import com.relay.server.endpoint.EndpointStatus;
import com.relay.server.event.Event;
import com.relay.server.event.EventRepository;
import com.relay.server.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Loads everything needed to send a claimed delivery, in one short read-only transaction.
 * The transaction ends before any HTTP call is made.
 */
@Component
@RequiredArgsConstructor
public class DeliveryContextLoader {

    private final EndpointRepository endpointRepository;
    private final EventRepository eventRepository;
    private final SecretCipher secretCipher;

    /**
     * Builds the send request, with the signing secret decrypted and the payload exactly as stored.
     * Empty if the endpoint or event is gone, or the endpoint is no longer ACTIVE.
     */
    @Transactional(readOnly = true)
    public Optional<SendRequest> load(ClaimedDelivery claimed) {
        Optional<Endpoint> endpoint = endpointRepository.findById(claimed.endpointId())
                .filter(e -> e.getStatus() == EndpointStatus.ACTIVE);
        Optional<Event> event = eventRepository.findById(claimed.eventId());
        if (endpoint.isEmpty() || event.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SendRequest(
                claimed.id(),
                claimed.eventId(),
                claimed.attemptNumber(),
                endpoint.get().getUrl(),
                secretCipher.decrypt(endpoint.get().getSigningSecret()),
                event.get().getPayload()));
    }
}
