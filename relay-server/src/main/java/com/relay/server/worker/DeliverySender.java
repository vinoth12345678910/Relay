package com.relay.server.worker;

import com.relay.server.config.RelayProperties;
import com.relay.server.security.SsrfGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * Builds, signs and sends ONE webhook. No database access, no transactions.
 * It returns a result; recording it is somebody else's job.
 */
@Component
@RequiredArgsConstructor
public class DeliverySender {

    private static final int SNIPPET_BYTES = 4096;

    private final HttpClient httpClient;
    private final SignatureService signer;
    private final SsrfGuard ssrfGuard;
    private final RelayProperties props;
    private final Clock clock;

    public SendResult send(SendRequest req) {
        long start = clock.millis();
        try {
            URI uri = URI.create(req.url());

            // Checked AGAIN at send time, not just at registration (DNS rebinding defense).
            ssrfGuard.validate(uri);

            long ts = clock.instant().getEpochSecond();

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(props.getHttpTotalTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Relay-Event-Id", req.eventId().toString())
                    .header("Relay-Delivery-Id", req.deliveryId().toString())
                    .header("Relay-Timestamp", Long.toString(ts))
                    .header("Relay-Attempt", Integer.toString(req.attemptNumber()))
                    .header("Relay-Signature", signer.sign(req.signingSecret(), ts, req.payload()))
                    .POST(HttpRequest.BodyPublishers.ofString(req.payload(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            String snippet;
            try (InputStream in = resp.body()) {
                snippet = new String(in.readNBytes(SNIPPET_BYTES), StandardCharsets.UTF_8);
            }

            int code = resp.statusCode();
            boolean ok = code >= 200 && code < 300;   // only 2xx is success; a 3xx is a FAILURE
            Duration retryAfter = null;
            if (!ok && (code == 429 || code == 503)) {     // the header means nothing on other statuses
                retryAfter = RetryAfterParser.parse(
                        resp.headers().firstValue("Retry-After").orElse(null),
                        clock,
                        Duration.ofSeconds(props.getRetryAfterMaxSeconds())).orElse(null);
            }
            return new SendResult(ok, code, ok ? null : "HTTP " + code,
                    clock.millis() - start, snippet, retryAfter);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();       // keep the shutdown signal alive
            return fail("interrupted", start);
        } catch (Exception e) {
            // timeout, connection refused, TLS error, SSRF rejection, bad URL...
            // Message only: never log the payload or secret.
            return fail(e.getClass().getSimpleName() + ": " + e.getMessage(), start);
        }
    }

    private SendResult fail(String error, long start) {
        return new SendResult(false, null, error, clock.millis() - start, null);
    }
}