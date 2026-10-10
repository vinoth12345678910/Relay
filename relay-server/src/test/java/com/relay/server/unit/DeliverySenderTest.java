// File: relay-server/src/test/java/com/relay/server/unit/DeliverySenderTest.java
package com.relay.server.unit;

import com.relay.server.config.HttpClientConfig;
import com.relay.server.config.RelayProperties;
import com.relay.server.security.SsrfGuard;
import com.relay.server.worker.DeliverySender;
import com.relay.server.worker.SendRequest;
import com.relay.server.worker.SendResult;
import com.relay.server.worker.SignatureService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link DeliverySender} against a real local HTTP server. Loopback is allowed through a
 * test-only SsrfGuard subclass; production config is unchanged.
 */
class DeliverySenderTest {

    private static final byte[] SECRET = "test-signing-secret".getBytes(StandardCharsets.UTF_8);
    private static final String PAYLOAD = "{\"a\":1,\"b\":[1,2]}";

    /** Test-only guard: skips the loopback block that the real guard enforces. */
    private static class LoopbackAllowedGuard extends SsrfGuard {
        @Override
        public void validate(URI uri) {
            // intentionally allows loopback
        }
    }

    private HttpServer server;
    private DeliverySender sender;
    private final SignatureService signer = new SignatureService();
    private final CopyOnWriteArrayList<ReceivedRequest> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger targetHits = new AtomicInteger();

    private record ReceivedRequest(com.sun.net.httpserver.Headers headers, String body) {}

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        sender = senderWith(new LoopbackAllowedGuard(), 400);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }

    private DeliverySender senderWith(SsrfGuard guard, int totalTimeoutMs) {
        RelayProperties props = new RelayProperties();
        props.setHttpConnectTimeoutMs(1000);
        props.setHttpTotalTimeoutMs(totalTimeoutMs);
        return new DeliverySender(new HttpClientConfig(props).deliveryHttpClient(), signer, guard, props,
                Clock.systemUTC());
    }

    private String start() {
        server.start();
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    private SendRequest request(String url) {
        return new SendRequest(UUID.randomUUID(), UUID.randomUUID(), 2, url, SECRET, PAYLOAD);
    }

    private void reply(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            ex.getResponseBody().write(body);
        }
        ex.close();
    }

    private void record(HttpExchange ex) throws IOException {
        received.add(new ReceivedRequest(ex.getRequestHeaders(),
                new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    @Test
    void status200IsSuccess() {
        server.createContext("/hook", ex -> { record(ex); reply(ex, 200, "ok".getBytes()); });
        String base = start();

        SendResult r = sender.send(request(base + "/hook"));

        assertThat(r.success()).isTrue();
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.error()).isNull();
        assertThat(r.responseSnippet()).isEqualTo("ok");
        assertThat(r.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void status500IsFailureWithStatusCode() {
        server.createContext("/hook", ex -> { record(ex); reply(ex, 500, "boom".getBytes()); });
        String base = start();

        SendResult r = sender.send(request(base + "/hook"));

        assertThat(r.success()).isFalse();
        assertThat(r.statusCode()).isEqualTo(500);
        assertThat(r.error()).isEqualTo("HTTP 500");
        assertThat(r.responseSnippet()).isEqualTo("boom");
    }

    @Test
    void handlerSlowerThanTotalTimeoutIsATimeoutFailure() {
        server.createContext("/hook", ex -> {
            try {
                Thread.sleep(2000);
                reply(ex, 200, new byte[0]);
            } catch (Exception ignored) {
                // client gave up
            }
        });
        String base = start();

        SendResult r = sender.send(request(base + "/hook"));

        assertThat(r.success()).isFalse();
        assertThat(r.statusCode()).isNull();
        assertThat(r.error()).containsIgnoringCase("timeout");
        assertThat(r.durationMs()).isLessThan(1500);
    }

    @Test
    void redirectIsAFailureAndIsNotFollowed() {
        server.createContext("/target", ex -> { targetHits.incrementAndGet(); reply(ex, 200, new byte[0]); });
        String base = start();
        server.createContext("/hook", ex -> {
            record(ex);
            ex.getResponseHeaders().add("Location", base + "/target");
            reply(ex, 302, new byte[0]);
        });

        SendResult r = sender.send(request(base + "/hook"));

        assertThat(r.success()).isFalse();
        assertThat(r.statusCode()).isEqualTo(302);
        assertThat(r.error()).isEqualTo("HTTP 302");
        assertThat(targetHits).hasValue(0);
        assertThat(received).hasSize(1);
    }

    @Test
    void sendsRelayHeadersAndAVerifiableSignatureOverTheExactBody() {
        server.createContext("/hook", ex -> { record(ex); reply(ex, 200, new byte[0]); });
        String base = start();
        SendRequest req = request(base + "/hook");

        sender.send(req);

        assertThat(received).hasSize(1);
        var headers = received.get(0).headers();
        assertThat(headers.getFirst("Relay-Event-Id")).isEqualTo(req.eventId().toString());
        assertThat(headers.getFirst("Relay-Delivery-Id")).isEqualTo(req.deliveryId().toString());
        assertThat(headers.getFirst("Relay-Attempt")).isEqualTo("2");
        assertThat(headers.getFirst("Content-Type")).isEqualTo("application/json");
        long ts = Long.parseLong(headers.getFirst("Relay-Timestamp"));
        assertThat(ts).isBetween(System.currentTimeMillis() / 1000 - 5, System.currentTimeMillis() / 1000 + 5);
        String body = received.get(0).body();
        assertThat(body).isEqualTo(PAYLOAD);
        assertThat(headers.getFirst("Relay-Signature")).isEqualTo(signer.sign(SECRET, ts, body)).startsWith("v1=");
        assertThat(signer.sign(SECRET, ts, body + " ")).isNotEqualTo(headers.getFirst("Relay-Signature"));
    }

    @Test
    void responseBodyIsTruncatedTo4Kb() {
        byte[] big = "x".repeat(10_000).getBytes(StandardCharsets.UTF_8);
        server.createContext("/hook", ex -> { record(ex); reply(ex, 200, big); });
        String base = start();

        SendResult r = sender.send(request(base + "/hook"));

        assertThat(r.success()).isTrue();
        assertThat(r.responseSnippet()).hasSize(4096);
    }

    @Test
    void realSsrfGuardBlocksLoopbackAndNothingIsSent() {
        server.createContext("/hook", ex -> { record(ex); reply(ex, 200, new byte[0]); });
        String base = start();
        DeliverySender strict = senderWith(new SsrfGuard(), 400);

        SendResult r = strict.send(request(base + "/hook"));

        assertThat(r.success()).isFalse();
        assertThat(r.statusCode()).isNull();
        assertThat(r.error()).contains("ValidationException");
        assertThat(received).isEmpty();
    }

    // ---------- Retry-After ----------

    private SendResult respondWith(int status, String retryAfter) {
        server.createContext("/hook", ex -> {
            record(ex);
            if (retryAfter != null) {
                ex.getResponseHeaders().add("Retry-After", retryAfter);
            }
            reply(ex, status, new byte[0]);
        });
        return sender.send(request(start() + "/hook"));
    }

    @Test
    void retryAfterSecondsOn429IsReported() {
        SendResult r = respondWith(429, "30");

        assertThat(r.success()).isFalse();
        assertThat(r.statusCode()).isEqualTo(429);
        assertThat(r.retryAfter()).isEqualTo(java.time.Duration.ofSeconds(30));
    }

    @Test
    void retryAfterHttpDateOn503IsReported() {
        String inTenMinutes = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(10));

        SendResult r = respondWith(503, inTenMinutes);

        assertThat(r.retryAfter()).isBetween(java.time.Duration.ofMinutes(9), java.time.Duration.ofMinutes(10));
    }

    @Test
    void hugeRetryAfterIsCappedAtTheConfiguredMax() {
        SendResult r = respondWith(503, "99999");

        assertThat(r.retryAfter()).isEqualTo(java.time.Duration.ofSeconds(3600));
    }

    @Test
    void retryAfterOnAnyOtherStatusIsIgnored() {
        assertThat(respondWith(500, "30").retryAfter()).isNull();
    }

    @Test
    void retryAfterOnASuccessIsIgnored() {
        assertThat(respondWith(200, "30").retryAfter()).isNull();
    }

    @Test
    void garbageRetryAfterIsIgnoredAndItIsStillAnOrdinaryFailure() {
        SendResult r = respondWith(429, "banana");

        assertThat(r.retryAfter()).isNull();
        assertThat(r.success()).isFalse();
        assertThat(r.statusCode()).isEqualTo(429);
        assertThat(r.error()).isEqualTo("HTTP 429");
    }

    @Test
    void missingRetryAfterOn429GivesNone() {
        assertThat(respondWith(429, null).retryAfter()).isNull();
    }
}
