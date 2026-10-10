// File: relay-server/src/test/java/com/relay/server/integration/ActuatorSecurityIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorSecurityIT extends AbstractIntegrationTest {

    @Test
    void healthIsPublicAndRevealsNothingAboutTheDatabase() {
        HttpResponse<String> r = send("GET", "/actuator/health", null, null, null);

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(json(r).get("status").asString()).isEqualTo("UP");
        assertThat(r.body()).doesNotContain("jdbc", "postgres", "url", "datasource", "components", "details");
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        assertThat(send("GET", "/actuator/env", null, null, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/actuator/beans", null, null, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/actuator/heapdump", null, null, null).statusCode()).isEqualTo(404);
    }
}
