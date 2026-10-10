// File: relay-server/src/test/java/com/relay/server/integration/ApiKeyAuthIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthIT extends AbstractIntegrationTest {

    private static final String BODY = "{\"type\":\"order.paid\",\"payload\":{\"a\":1}}";

    @Test
    void missingKeyIsUnauthorized() {
        assertThat(send("POST", "/api/v1/events", null, BODY, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/events/" + UUID.randomUUID(), null, null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void wrongKeyIsUnauthorized() {
        var post = send("POST", "/api/v1/events", "rl_live_not-a-real-key", BODY, null);
        var get = send("GET", "/api/v1/events/" + UUID.randomUUID(), "rl_live_not-a-real-key", null, null);

        assertThat(post.statusCode()).isEqualTo(401);
        assertThat(get.statusCode()).isEqualTo(401);
        assertThat(post.body()).doesNotContain("rl_live_not-a-real-key");
    }

    @Test
    void adminTokenDoesNotAuthenticateEventRoutes() {
        assertThat(send("POST", "/api/v1/events", ADMIN_TOKEN, BODY, null).statusCode()).isEqualTo(401);
    }

    @Test
    void appCreationRequiresAdminToken() {
        String body = "{\"name\":\"x\"}";

        assertThat(send("POST", "/api/v1/apps", null, body, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/apps", "wrong-admin-token", body, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/apps", ADMIN_TOKEN, body, null).statusCode()).isEqualTo(201);
    }

    @Test
    void validKeyAuthenticatesAtTheFilterNotJustTheController() {
        TestApp app = createApp();

        assertThat(get("/api/v1/apps/" + app.id(), app.apiKey()).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/apps/" + app.id(), null).statusCode()).isEqualTo(401);
    }
}
