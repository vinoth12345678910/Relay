// File: relay-server/src/test/java/com/relay/server/integration/AbstractIntegrationTest.java
package com.relay.server.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Base for integration tests: one shared real Postgres (Testcontainers) for the whole JVM,
 * the full app on a random port, and small helpers that talk to it over real HTTP.
 * Each test creates its own app, so tests never need to clean up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// The delivery poller is off unless a test is about the poller; otherwise it would claim other tests' rows.
@TestPropertySource(properties = "relay.worker.enabled=false")
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_TOKEN = "it-admin-token";
    // Public literal IPs (TEST-NET-3) so SsrfGuard passes without any DNS lookup.
    private static final String ENDPOINT_URL = "https://203.0.113.10/hooks";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("relay.admin-token", () -> ADMIN_TOKEN);
        registry.add("relay.secret-encryption-key", () -> "aW50ZWdyYXRpb24tdGVzdC1rZXktMzItYnl0ZXMhIQ==");
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    /** An app created through the real admin API. */
    protected record TestApp(UUID id, String apiKey) {}

    protected TestApp createApp() {
        String body = "{\"name\":\"it-app-" + UUID.randomUUID() + "\"}";
        HttpResponse<String> r = send("POST", "/api/v1/apps", ADMIN_TOKEN, body, null);
        JsonNode json = json(r);
        return new TestApp(UUID.fromString(json.get("id").asString()), json.get("apiKey").asString());
    }

    /** Creates an endpoint with the given filter and returns its id. */
    protected UUID createEndpoint(TestApp app, String filter) {
        return UUID.fromString(createEndpointJson(app, filter, ENDPOINT_URL).get("id").asString());
    }

    /** Creates an endpoint at {@code url} and returns the full create response (including the one-time secret). */
    protected JsonNode createEndpointJson(TestApp app, String filter, String url) {
        String body = "{\"url\":\"" + url + "\",\"eventTypeFilter\":\"" + filter + "\"}";
        HttpResponse<String> r = send("POST", "/api/v1/endpoints", app.apiKey(), body, null);
        return json(r);
    }

    protected void pauseEndpoint(TestApp app, UUID endpointId) {
        send("POST", "/api/v1/endpoints/" + endpointId + "/pause", app.apiKey(), null, null);
    }

    protected HttpResponse<String> publish(TestApp app, String type, String payloadJson, String idempotencyKey) {
        String body = "{\"type\":\"" + type + "\",\"payload\":" + payloadJson + "}";
        return send("POST", "/api/v1/events", app.apiKey(), body, idempotencyKey);
    }

    protected HttpResponse<String> get(String path, String apiKey) {
        return send("GET", path, apiKey, null, null);
    }

    /** Sends a request; a null token means no Authorization header. */
    protected HttpResponse<String> send(String method, String path, String token, String body, String idempotencyKey) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try {
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("HTTP call failed: " + method + " " + path, e);
        }
    }

    protected JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    protected long countEvents(UUID appId) {
        return jdbc.queryForObject("select count(*) from events where app_id = ?", Long.class, appId);
    }

    protected long countDeliveries(UUID appId) {
        return jdbc.queryForObject(
                "select count(*) from deliveries d join events e on e.id = d.event_id where e.app_id = ?",
                Long.class, appId);
    }
}
