// File: relay-server/src/test/java/com/relay/server/integration/IdempotencyRaceIT.java
package com.relay.server.integration;

import com.relay.server.common.ConflictException;
import com.relay.server.event.EventRepository;
import com.relay.server.event.EventService;
import com.relay.server.event.PublishResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Deterministically forces the unique-violation path in {@link EventService#publish}.
 *
 * <p>A race loser is a caller whose pre-check ("does this key exist?") ran before the winner
 * committed. We simulate it by making the first {@code findByAppIdAndIdempotencyKey} call return
 * empty even though the event already exists. The insert then hits the unique index, the
 * transaction rolls back, and the catch block must re-read the winner and replay it.
 * Two lookups in total prove the catch path ran.
 */
class IdempotencyRaceIT extends AbstractIntegrationTest {

    private static final String KEY = "race-key";

    @Autowired
    private EventService eventService;

    /** The wrapper registered by {@link EventRepositorySpyConfig}; delegates to the real repository. */
    @Autowired
    private EventRepository eventRepository;

    /**
     * Wraps the real repository in a Mockito mock that delegates every call to it, so single
     * calls can be stubbed and counted. {@code @MockitoSpyBean} cannot be used here: Mockito
     * cannot call "real methods" on a Spring Data interface proxy.
     */
    @TestConfiguration
    static class EventRepositorySpyConfig {

        static volatile EventRepository real;

        @Bean
        static BeanPostProcessor eventRepositorySpy() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof EventRepository repository) {
                        real = repository;
                        return mock(EventRepository.class,
                                withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(repository)));
                    }
                    return bean;
                }
            };
        }
    }

    @Test
    void raceLoserWithSamePayloadReplaysTheWinner() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        PublishResult winner = eventService.publish(app.id(), "order.paid", payload("{\"a\":1}"), KEY);
        assertThat(winner.replayed()).isFalse();
        loseTheCheckOnce(app.id());

        PublishResult loser = eventService.publish(app.id(), "order.paid", payload("{\"a\":1}"), KEY);

        assertThat(loser.replayed()).isTrue();
        assertThat(loser.event().getId()).isEqualTo(winner.event().getId());
        assertThat(loser.deliveryCount()).isEqualTo(winner.deliveryCount()).isEqualTo(1);
        assertThat(countEvents(app.id())).isEqualTo(1);
        assertThat(countDeliveries(app.id())).isEqualTo(1);
        verify(eventRepository, times(2)).findByAppIdAndIdempotencyKey(app.id(), KEY);
    }

    @Test
    void raceLoserWithDifferentPayloadIsConflict() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        eventService.publish(app.id(), "order.paid", payload("{\"a\":1}"), KEY);
        loseTheCheckOnce(app.id());

        assertThatThrownBy(() -> eventService.publish(app.id(), "order.paid", payload("{\"a\":2}"), KEY))
                .isInstanceOf(ConflictException.class);

        assertThat(countEvents(app.id())).isEqualTo(1);
        assertThat(countDeliveries(app.id())).isEqualTo(1);
        verify(eventRepository, times(2)).findByAppIdAndIdempotencyKey(app.id(), KEY);
    }

    /** First lookup claims the key is free; every later lookup is real. Counts start from zero. */
    private void loseTheCheckOnce(UUID appId) {
        clearInvocations(eventRepository);
        doReturn(Optional.empty()).doAnswer(AdditionalAnswers.delegatesTo(EventRepositorySpyConfig.real))
                .when(eventRepository).findByAppIdAndIdempotencyKey(eq(appId), any());
    }

    private JsonNode payload(String json) {
        return mapper.readTree(json);
    }
}
