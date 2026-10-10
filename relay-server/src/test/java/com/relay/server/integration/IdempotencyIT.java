// File: relay-server/src/test/java/com/relay/server/integration/IdempotencyIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIT extends AbstractIntegrationTest {

    private static final String REPLAY_HEADER = "Idempotent-Replayed";

    @Test
    void sameKeyAndPayloadReplaysWithoutNewDeliveries() {
        TestApp app = createApp();
        createEndpoint(app, "*");

        HttpResponse<String> first = publish(app, "order.paid", "{\"a\":1}", "key-1");
        HttpResponse<String> second = publish(app, "order.paid", "{\"a\":1}", "key-1");

        assertThat(first.statusCode()).isEqualTo(202);
        assertThat(second.statusCode()).isEqualTo(202);
        assertThat(json(second).get("id")).isEqualTo(json(first).get("id"));
        assertThat(first.headers().firstValue(REPLAY_HEADER)).isEmpty();
        assertThat(second.headers().firstValue(REPLAY_HEADER)).contains("true");
        assertThat(json(second).get("replayed").asBoolean()).isTrue();
        assertThat(json(second).get("deliveryCount").asLong()).isEqualTo(1);
        assertThat(countEvents(app.id())).isEqualTo(1);
        assertThat(countDeliveries(app.id())).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentPayloadIsConflict() {
        TestApp app = createApp();
        publish(app, "order.paid", "{\"a\":1}", "key-2");

        HttpResponse<String> conflict = publish(app, "order.paid", "{\"a\":2}", "key-2");

        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json(conflict).get("code").asString()).isEqualTo("CONFLICT");
        assertThat(countEvents(app.id())).isEqualTo(1);
    }

    @Test
    void sameKeyInDifferentAppsIsIndependent() {
        TestApp appA = createApp();
        TestApp appB = createApp();

        HttpResponse<String> a = publish(appA, "order.paid", "{\"a\":1}", "shared-key");
        HttpResponse<String> b = publish(appB, "order.paid", "{\"a\":1}", "shared-key");

        assertThat(a.statusCode()).isEqualTo(202);
        assertThat(b.statusCode()).isEqualTo(202);
        assertThat(json(b).get("replayed").asBoolean()).isFalse();
        assertThat(json(b).get("id")).isNotEqualTo(json(a).get("id"));
    }

    @Test
    void blankKeyMeansNoIdempotency() {
        TestApp app = createApp();

        publish(app, "order.paid", "{\"a\":1}", "  ");
        publish(app, "order.paid", "{\"a\":1}", "  ");

        assertThat(countEvents(app.id())).isEqualTo(2);
    }
}
