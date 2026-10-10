// File: relay-server/src/test/java/com/relay/server/integration/EndpointApiIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointApiIT extends AbstractIntegrationTest {

    private static final String URL = "https://203.0.113.10/hooks";

    private HttpResponse<String> create(TestApp app, String url) {
        return send("POST", "/api/v1/endpoints", app.apiKey(), "{\"url\":\"" + url + "\"}", null);
    }

    @Test
    void signingSecretIsReturnedOnceAndStoredEncrypted() {
        TestApp app = createApp();

        JsonNode created = json(create(app, URL));
        String secret = created.get("signingSecret").asString();
        String id = created.get("id").asString();

        assertThat(secret).startsWith("whsec_").hasSizeGreaterThan(40);
        assertThat(created.get("eventTypeFilter").asString()).isEqualTo("*");
        assertThat(created.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(get("/api/v1/endpoints/" + id, app.apiKey()).body()).doesNotContain("signingSecret", secret);
        assertThat(get("/api/v1/endpoints", app.apiKey()).body()).doesNotContain("signingSecret", secret);
        byte[] stored = jdbc.queryForObject("select signing_secret from endpoints where id = ?::uuid", byte[].class, id);
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(secret.substring(6));
        assertThat(stored).hasSize(12 + 32 + 16);   // iv + secret + GCM tag
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/x", "http://localhost/x", "http://10.0.0.5/x", "http://192.168.1.1/x",
            "http://172.16.0.1/x", "http://169.254.169.254/latest/meta-data", "http://[::1]/x",
            "http://100.64.0.1/x", "ftp://203.0.113.10/x", "file:///etc/passwd", "http://user:pw@203.0.113.10/x",
            "not a url"})
    void unsafeUrlsAreRejected(String url) {
        TestApp app = createApp();

        HttpResponse<String> r = create(app, url);

        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(json(r).get("fieldErrors").get(0).get("field").asString()).isEqualTo("url");
        assertThat(jdbc.queryForObject("select count(*) from endpoints where app_id = ?", Long.class, app.id())).isZero();
    }

    @Test
    void otherAppsEndpointIsNotFoundOnEveryVerb() {
        TestApp a = createApp();
        TestApp b = createApp();
        UUID id = createEndpoint(a, "*");
        String path = "/api/v1/endpoints/" + id;

        assertThat(get(path, b.apiKey()).statusCode()).isEqualTo(404);
        assertThat(send("PATCH", path, b.apiKey(), "{\"description\":\"hijack\"}", null).statusCode()).isEqualTo(404);
        assertThat(send("POST", path + "/pause", b.apiKey(), null, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", path + "/resume", b.apiKey(), null, null).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", path, b.apiKey(), null, null).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/endpoints", b.apiKey()).body()).isEqualTo("[]");
        assertThat(json(get(path, a.apiKey())).get("status").asString()).isEqualTo("ACTIVE");
        assertThat(json(get(path, a.apiKey())).get("description").isNull()).isTrue();
    }

    @Test
    void updatePatchesOnlyGivenFieldsAndRevalidatesUrl() {
        TestApp app = createApp();
        UUID id = createEndpoint(app, "order.*");
        String path = "/api/v1/endpoints/" + id;

        JsonNode updated = json(send("PATCH", path, app.apiKey(), "{\"description\":\"hi\"}", null));
        assertThat(updated.get("description").asString()).isEqualTo("hi");
        assertThat(updated.get("eventTypeFilter").asString()).isEqualTo("order.*");

        assertThat(send("PATCH", path, app.apiKey(), "{\"url\":\"http://127.0.0.1/x\"}", null).statusCode()).isEqualTo(400);
        assertThat(send("PATCH", path, app.apiKey(), "{\"eventTypeFilter\":\"Bad Filter\"}", null).statusCode()).isEqualTo(400);
        assertThat(json(get(path, app.apiKey())).get("url").asString()).isEqualTo(URL);
    }

    @Test
    void pauseAndResumeToggleStatusAndFailureCounter() {
        TestApp app = createApp();
        UUID id = createEndpoint(app, "*");
        String path = "/api/v1/endpoints/" + id;
        jdbc.update("update endpoints set consecutive_failures = 7 where id = ?", id);

        JsonNode paused = json(send("POST", path + "/pause", app.apiKey(), null, null));
        assertThat(paused.get("status").asString()).isEqualTo("PAUSED");
        assertThat(paused.get("pausedReason").asString()).isEqualTo("Paused manually");
        JsonNode withReason = json(send("POST", path + "/pause", app.apiKey(), "{\"reason\":\"maintenance\"}", null));
        assertThat(withReason.get("pausedReason").asString()).isEqualTo("maintenance");

        JsonNode resumed = json(send("POST", path + "/resume", app.apiKey(), null, null));
        assertThat(resumed.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(resumed.get("pausedReason").isNull()).isTrue();
        assertThat(resumed.get("consecutiveFailures").asInt()).isZero();
    }

    @Test
    void deleteIsSoftAndHidesTheEndpoint() {
        TestApp app = createApp();
        UUID id = createEndpoint(app, "*");
        String path = "/api/v1/endpoints/" + id;

        assertThat(send("DELETE", path, app.apiKey(), null, null).statusCode()).isEqualTo(204);

        assertThat(get(path, app.apiKey()).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/endpoints", app.apiKey()).body()).isEqualTo("[]");
        assertThat(send("DELETE", path, app.apiKey(), null, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", path + "/resume", app.apiKey(), null, null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select status from endpoints where id = ?", String.class, id)).isEqualTo("DISABLED");
    }

    @Test
    void malformedAndInvalidBodiesAreBadRequests() {
        TestApp app = createApp();

        assertThat(send("POST", "/api/v1/endpoints", app.apiKey(), "{not json", null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/v1/endpoints", app.apiKey(), "{}", null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/v1/endpoints/not-a-uuid", app.apiKey(), null, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/v1/nope", app.apiKey(), null, null).statusCode()).isEqualTo(404);
    }

    @Test
    void appsAreReadableOnlyByThemselves() {
        TestApp a = createApp();
        TestApp b = createApp();

        JsonNode own = json(get("/api/v1/apps/" + a.id(), a.apiKey()));
        assertThat(own.get("id").asString()).isEqualTo(a.id().toString());
        assertThat(own.has("apiKeyHash")).isFalse();
        assertThat(own.has("apiKeyPrefix")).isFalse();
        assertThat(get("/api/v1/apps/" + a.id(), b.apiKey()).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/api/v1/apps", ADMIN_TOKEN, "{\"name\":\"  \"}", null).statusCode()).isEqualTo(400);
    }

    @Test
    void apiKeyIsStoredHashedAndNeverEchoed() {
        TestApp app = createApp();

        String stored = jdbc.queryForObject("select api_key_hash from apps where id = ?", String.class, app.id());

        assertThat(stored).isNotEqualTo(app.apiKey()).doesNotContain(app.apiKey()).hasSize(64);
        assertThat(app.apiKey()).startsWith("rl_live_");
    }
}
