// File: mock-receiver/src/main/java/com/relay/receiver/HookController.java
package com.relay.receiver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Random;
import java.util.function.Supplier;

/**
 * A webhook receiver that misbehaves on command, one route per behaviour.
 */
@RestController
public class HookController {

    private final ReceiverState state;
    private final Random random = new Random();

    public HookController(ReceiverState state) {
        this.state = state;
    }

    private static ResponseEntity<String> reply(int code, String text) {
        return ResponseEntity.status(code).body(text);
    }

    /** Wraps every behaviour: tracks concurrency and records the hit with the status we returned. */
    private ResponseEntity<String> track(HttpServletRequest req, byte[] body, Supplier<ResponseEntity<String>> action) {
        String did = req.getHeader("Relay-Delivery-Id");
        state.enter(did);
        ResponseEntity<String> resp = null;
        try {
            resp = action.get();
            return resp;
        } finally {
            state.leave(did);
            state.record(new ReceiverState.Hit(Instant.now(), req.getRequestURI(), did,
                    req.getHeader("Relay-Event-Id"), req.getHeader("Relay-Attempt"),
                    resp == null ? 500 : resp.getStatusCode().value(), ReceiverState.bodyOf(body)));
        }
    }

    @PostMapping("/hook/ok")
    public ResponseEntity<String> ok(HttpServletRequest r, @RequestBody(required = false) byte[] b) {
        return track(r, b, () -> reply(200, "ok"));
    }

    /** Fails the first n requests PER DELIVERY ID, then succeeds. */
    @PostMapping("/hook/fail-first/{n}")
    public ResponseEntity<String> failFirst(HttpServletRequest r, @RequestBody(required = false) byte[] b, @PathVariable int n) {
        String did = r.getHeader("Relay-Delivery-Id");
        return track(r, b, () -> state.priorHits(did) < n ? reply(500, "fail") : reply(200, "ok"));
    }

    @PostMapping("/hook/always-500")
    public ResponseEntity<String> always500(HttpServletRequest r, @RequestBody(required = false) byte[] b) {
        return track(r, b, () -> reply(500, "always failing"));
    }

    @PostMapping("/hook/slow/{seconds}")
    public ResponseEntity<String> slow(HttpServletRequest r, @RequestBody(required = false) byte[] b, @PathVariable int seconds) {
        return track(r, b, () -> {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return reply(200, "slow ok");
        });
    }

    @PostMapping("/hook/flaky")
    public ResponseEntity<String> flaky(HttpServletRequest r, @RequestBody(required = false) byte[] b,
                                        @RequestParam(defaultValue = "0.3") double p) {
        return track(r, b, () -> random.nextDouble() < p ? reply(500, "flaky fail") : reply(200, "ok"));
    }

    /** 302 to the target below. If Relay ever follows it, redirectTargetHits goes above 0. */
    @PostMapping("/hook/redirect")
    public ResponseEntity<String> redirect(HttpServletRequest r, @RequestBody(required = false) byte[] b) {
        return track(r, b, () -> ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/hook/redirect-target").body("moved"));
    }

    @RequestMapping("/hook/redirect-target")
    public ResponseEntity<String> redirectTarget() {
        state.redirectTargetHit();
        return reply(200, "you should never see this from Relay");
    }

    @PostMapping("/hook/gone")
    public ResponseEntity<String> gone(HttpServletRequest r, @RequestBody(required = false) byte[] b) {
        return track(r, b, () -> reply(410, "gone"));
    }

    /** 429 with a Retry-After header, then success on the next try for that delivery. */
    @PostMapping("/hook/busy/{seconds}")
    public ResponseEntity<String> busy(HttpServletRequest r, @RequestBody(required = false) byte[] b, @PathVariable int seconds) {
        String did = r.getHeader("Relay-Delivery-Id");
        return track(r, b, () -> state.priorHits(did) == 0
                ? ResponseEntity.status(429).header("Retry-After", String.valueOf(seconds)).body("busy")
                : reply(200, "ok"));
    }

    /** Behaviour flipped at runtime by POST /config/switch?ok=true|false. Used for the breaker demo. */
    @PostMapping("/hook/switch")
    public ResponseEntity<String> sw(HttpServletRequest r, @RequestBody(required = false) byte[] b) {
        return track(r, b, () -> state.switchOk ? reply(200, "ok") : reply(500, "switch is off"));
    }

    /** The reference receiver: checks the signature as a real consumer would. */
    @PostMapping("/hook/verify")
    public ResponseEntity<String> verify(HttpServletRequest r, @RequestBody(required = false) byte[] b) {
        return track(r, b, () -> {
            byte[] key = state.secret;
            if (key == null) {
                return reply(500, "secret not configured: POST /config/secret first");
            }
            String ts = r.getHeader("Relay-Timestamp");
            String sig = r.getHeader("Relay-Signature");
            if (ts == null || sig == null) {
                return reply(401, "missing headers");
            }
            long age;
            try {
                age = Math.abs(Instant.now().getEpochSecond() - Long.parseLong(ts));
            } catch (NumberFormatException e) {
                return reply(401, "bad timestamp");
            }
            if (age > 300) {
                return reply(401, "timestamp too old");
            }
            String body = b == null ? "" : new String(b, StandardCharsets.UTF_8);
            byte[] expected = hmacHex(key, ts + "." + body).getBytes(StandardCharsets.UTF_8);
            for (String part : sig.split("[,\\s]+")) {
                if (part.startsWith("v1=")
                        && MessageDigest.isEqual(expected, part.substring(3).getBytes(StandardCharsets.UTF_8))) {
                    return reply(200, "signature ok");
                }
            }
            return reply(401, "bad signature");
        });
    }

    static String hmacHex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
