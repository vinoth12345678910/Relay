// File: relay-server/src/test/java/com/relay/server/integration/DeliveryApiIT.java
package com.relay.server.integration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryApiIT extends AbstractIntegrationTest {

    /** Publishes {@code n} events to the app's endpoints and returns their delivery ids, oldest first. */
    private List<UUID> publishAndCollect(TestApp app, int n) {
        for (int i = 0; i < n; i++) {
            publish(app, "order.paid", "{\"n\":" + i + "}", null);
        }
        return jdbc.queryForList("select d.id from deliveries d join events e on e.id = d.event_id "
                + "where e.app_id = ? order by d.created_at, d.id", UUID.class, app.id());
    }

    private void setStatus(UUID deliveryId, String status, int attemptCount) {
        jdbc.update("update deliveries set status = ?, attempt_count = ? where id = ?", status, attemptCount, deliveryId);
    }

    private JsonNode listJson(TestApp app, String query) {
        HttpResponse<String> r = get("/api/v1/deliveries" + query, app.apiKey());
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return json(r);
    }

    private List<String> ids(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("items").forEach(i -> out.add(i.get("id").asString()));
        return out;
    }

    private HttpResponse<String> replay(TestApp app, UUID deliveryId) {
        return send("POST", "/api/v1/deliveries/" + deliveryId + "/replay", app.apiKey(), null, null);
    }

    // ---------- list ----------

    @Test
    void listFiltersByStatusAndEndpoint() {
        TestApp app = createApp();
        UUID endpointA = createEndpoint(app, "*");
        UUID endpointB = createEndpoint(app, "*");
        publishAndCollect(app, 3);
        List<UUID> a = jdbc.queryForList("select d.id from deliveries d where d.endpoint_id = ? order by d.created_at, d.id", UUID.class, endpointA);
        List<UUID> b = jdbc.queryForList("select d.id from deliveries d where d.endpoint_id = ? order by d.created_at, d.id", UUID.class, endpointB);
        setStatus(a.get(0), "DEAD", 3);
        setStatus(a.get(1), "DEAD", 3);
        setStatus(a.get(2), "SUCCEEDED", 1);
        setStatus(b.get(0), "DEAD", 3);

        assertThat(listJson(app, "").get("items")).hasSize(6);
        assertThat(listJson(app, "?status=DEAD").get("items")).hasSize(3);
        assertThat(listJson(app, "?status=dead").get("items")).hasSize(3);
        assertThat(listJson(app, "?status=PENDING").get("items")).hasSize(2);
        assertThat(listJson(app, "?endpointId=" + endpointA).get("items")).hasSize(3);
        JsonNode both = listJson(app, "?status=DEAD&endpointId=" + endpointA);
        assertThat(ids(both)).containsExactlyInAnyOrder(a.get(0).toString(), a.get(1).toString());
        both.get("items").forEach(i -> assertThat(i.get("status").asString()).isEqualTo("DEAD"));
    }

    @Test
    void threePagesHaveNoDuplicatesOrGapsEvenWhenNewDeliveriesArriveBetweenCalls() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        List<UUID> original = publishAndCollect(app, 13);

        JsonNode page1 = listJson(app, "?limit=5");
        publishAndCollect(app, 4);                        // newer rows land at the top while the client is reading
        JsonNode page2 = listJson(app, "?limit=5&cursor=" + page1.get("nextCursor").asString());
        publishAndCollect(app, 2);
        JsonNode page3 = listJson(app, "?limit=5&cursor=" + page2.get("nextCursor").asString());

        List<String> all = new ArrayList<>();
        all.addAll(ids(page1));
        all.addAll(ids(page2));
        all.addAll(ids(page3));
        assertThat(ids(page1)).hasSize(5);
        assertThat(ids(page2)).hasSize(5);
        assertThat(ids(page3)).hasSize(3);
        assertThat(page3.get("nextCursor").isNull()).isTrue();
        assertThat(new HashSet<>(all)).as("no duplicates").hasSize(13);
        assertThat(all).as("no gaps, newest first").containsExactlyElementsOf(
                original.reversed().stream().map(UUID::toString).toList());
    }

    @Test
    void rowsSharingTheSameTimestampPageCleanly() {
        TestApp app = createApp();
        UUID endpointId = createEndpoint(app, "*");
        jdbc.update("insert into events (app_id, type, payload, payload_hash, created_at) "
                + "select ?, 'bulk.event', '{}', 'h', now() from generate_series(1, 7)", app.id());
        jdbc.update("insert into deliveries (event_id, endpoint_id, status, next_attempt_at, created_at, updated_at) "
                + "select id, ?, 'SUCCEEDED', now(), timestamptz '2026-01-01 00:00:00+00', now() from events where app_id = ?",
                endpointId, app.id());

        List<String> all = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            JsonNode page = listJson(app, "?limit=3" + (cursor == null ? "" : "&cursor=" + cursor));
            all.addAll(ids(page));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(all).hasSize(7);
        assertThat(new HashSet<>(all)).hasSize(7);
    }

    @Test
    void invalidListParametersAreBadRequests() {
        TestApp app = createApp();

        HttpResponse<String> badCursor = get("/api/v1/deliveries?cursor=garbage", app.apiKey());
        assertThat(badCursor.statusCode()).isEqualTo(400);
        assertThat(json(badCursor).get("fieldErrors").get(0).get("field").asString()).isEqualTo("cursor");
        assertThat(get("/api/v1/deliveries?status=BOGUS", app.apiKey()).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/deliveries?limit=0", app.apiKey()).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/deliveries?limit=201", app.apiKey()).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/deliveries?limit=200", app.apiKey()).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/deliveries?endpointId=not-a-uuid", app.apiKey()).statusCode()).isEqualTo(400);
    }

    // ---------- detail and attempts ----------

    @Test
    void detailShowsTheDeliveryAndUnknownIdsAre404() {
        TestApp app = createApp();
        UUID endpointId = createEndpoint(app, "*");
        UUID id = publishAndCollect(app, 1).get(0);

        JsonNode d = json(get("/api/v1/deliveries/" + id, app.apiKey()));

        assertThat(d.get("id").asString()).isEqualTo(id.toString());
        assertThat(d.get("endpointId").asString()).isEqualTo(endpointId.toString());
        assertThat(d.get("status").asString()).isEqualTo("PENDING");
        assertThat(d.get("attemptCount").asInt()).isZero();
        assertThat(d.get("attemptBase").asInt()).isZero();
        assertThat(d.get("nextAttemptAt").isNull()).isFalse();
        assertThat(get("/api/v1/deliveries/" + UUID.randomUUID(), app.apiKey()).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/deliveries/not-a-uuid", app.apiKey()).statusCode()).isEqualTo(400);
    }

    @Test
    void attemptsComeBackInOrderWithTheirDetails() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        UUID id = publishAndCollect(app, 1).get(0);
        for (int n : new int[]{3, 1, 2}) {
            jdbc.update("insert into delivery_attempts (delivery_id, attempt_number, started_at, duration_ms, status_code, error, response_snippet, created_at) "
                    + "values (?, ?, now(), ?, ?, ?, ?, now())", id, n, 10 * n, n == 3 ? 200 : 500, n == 3 ? null : "HTTP 500", "body" + n);
        }

        JsonNode attempts = json(get("/api/v1/deliveries/" + id + "/attempts", app.apiKey()));

        assertThat(attempts).hasSize(3);
        assertThat(attempts.get(0).get("attemptNumber").asInt()).isEqualTo(1);
        assertThat(attempts.get(1).get("attemptNumber").asInt()).isEqualTo(2);
        assertThat(attempts.get(2).get("attemptNumber").asInt()).isEqualTo(3);
        assertThat(attempts.get(0).get("statusCode").asInt()).isEqualTo(500);
        assertThat(attempts.get(0).get("error").asString()).isEqualTo("HTTP 500");
        assertThat(attempts.get(0).get("durationMs").asLong()).isEqualTo(10);
        assertThat(attempts.get(0).get("responseSnippet").asString()).isEqualTo("body1");
        assertThat(attempts.get(2).get("error").isNull()).isTrue();
    }

    // ---------- replay ----------

    @Test
    void deadAndSucceededDeliveriesCanBeReplayedWithAFreshBudget() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        List<UUID> ids = publishAndCollect(app, 2);
        setStatus(ids.get(0), "DEAD", 9);
        setStatus(ids.get(1), "SUCCEEDED", 1);

        HttpResponse<String> dead = replay(app, ids.get(0));
        HttpResponse<String> succeeded = replay(app, ids.get(1));

        assertThat(dead.statusCode()).isEqualTo(202);
        JsonNode d = json(dead);
        assertThat(d.get("message").asString()).isEqualTo("Replay queued.");
        assertThat(d.get("delivery").get("status").asString()).isEqualTo("PENDING");
        assertThat(d.get("delivery").get("attemptBase").asInt()).isEqualTo(9);
        assertThat(d.get("delivery").get("attemptCount").asInt()).as("attempt_count only grows").isEqualTo(9);
        assertThat(succeeded.statusCode()).isEqualTo(202);
        assertThat(json(succeeded).get("delivery").get("attemptBase").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select locked_until is null from deliveries where id = ?", Boolean.class, ids.get(0))).isTrue();
    }

    @Test
    void aSecondReplayRightAfterTheFirstIsRefused() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        UUID id = publishAndCollect(app, 1).get(0);
        setStatus(id, "SUCCEEDED", 1);

        assertThat(replay(app, id).statusCode()).isEqualTo(202);
        HttpResponse<String> again = replay(app, id);

        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select attempt_base from deliveries where id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void pendingAndInFlightDeliveriesCannotBeReplayed() {
        TestApp app = createApp();
        createEndpoint(app, "*");
        List<UUID> ids = publishAndCollect(app, 2);
        setStatus(ids.get(1), "IN_FLIGHT", 1);

        HttpResponse<String> pending = replay(app, ids.get(0));
        HttpResponse<String> inFlight = replay(app, ids.get(1));

        assertThat(pending.statusCode()).isEqualTo(409);
        assertThat(json(pending).get("code").asString()).isEqualTo("CONFLICT");
        assertThat(pending.body()).contains("PENDING");
        assertThat(inFlight.statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select status from deliveries where id = ?", String.class, ids.get(1))).isEqualTo("IN_FLIGHT");
        assertThat(jdbc.queryForObject("select attempt_base from deliveries where id = ?", Integer.class, ids.get(1))).isZero();
    }

    @Test
    void replayToAPausedEndpointIsQueuedWithAnExplanation() {
        TestApp app = createApp();
        UUID endpointId = createEndpoint(app, "*");
        UUID id = publishAndCollect(app, 1).get(0);
        setStatus(id, "DEAD", 3);
        pauseEndpoint(app, endpointId);

        JsonNode r = json(replay(app, id));

        assertThat(r.get("delivery").get("status").asString()).isEqualTo("PENDING");
        assertThat(r.get("message").asString()).contains("PAUSED");
    }

    @Test
    void twoSimultaneousReplaysOnlyOneWins() throws Exception {
        TestApp app = createApp();
        createEndpoint(app, "*");
        UUID id = publishAndCollect(app, 1).get(0);
        setStatus(id, "DEAD", 3);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return replay(app, id).statusCode();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(202, 409);
        assertThat(jdbc.queryForObject("select attempt_base from deliveries where id = ?", Integer.class, id)).isEqualTo(3);
    }

    // ---------- tenant isolation and auth ----------

    @Test
    void anotherAppsDeliveryIsNotFoundOnEveryRoute() {
        TestApp owner = createApp();
        TestApp other = createApp();
        createEndpoint(owner, "*");
        UUID id = publishAndCollect(owner, 1).get(0);
        setStatus(id, "DEAD", 3);
        String path = "/api/v1/deliveries/" + id;

        assertThat(get(path, other.apiKey()).statusCode()).isEqualTo(404);
        assertThat(get(path + "/attempts", other.apiKey()).statusCode()).isEqualTo(404);
        assertThat(replay(other, id).statusCode()).isEqualTo(404);
        assertThat(listJson(other, "").get("items")).isEmpty();
        assertThat(listJson(other, "?status=DEAD").get("items")).isEmpty();
        assertThat(get(path, owner.apiKey()).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from deliveries where id = ?", String.class, id)).isEqualTo("DEAD");
    }

    @Test
    void everyRouteRequiresAnApiKey() {
        UUID id = UUID.randomUUID();

        assertThat(send("GET", "/api/v1/deliveries", null, null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/deliveries/" + id, null, null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/deliveries/" + id + "/attempts", null, null, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/deliveries/" + id + "/replay", null, null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/deliveries", "rl_live_wrong", null, null).statusCode()).isEqualTo(401);
    }
}
