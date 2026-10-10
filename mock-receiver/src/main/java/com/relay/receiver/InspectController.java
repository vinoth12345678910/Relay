// File: mock-receiver/src/main/java/com/relay/receiver/InspectController.java
package com.relay.receiver;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * Read-only view of what the receiver saw, plus the knobs the demo turns.
 */
@RestController
public class InspectController {

    private final ReceiverState state;

    public InspectController(ReceiverState state) {
        this.state = state;
    }

    @GetMapping(value = "/inspect", produces = "application/json")
    public String inspect(@RequestParam(defaultValue = "200") int last) {
        return "[" + state.latest(last).stream().map(h ->
                "{\"at\":" + ReceiverState.esc(h.at().toString())
                        + ",\"path\":" + ReceiverState.esc(h.path())
                        + ",\"deliveryId\":" + ReceiverState.esc(h.deliveryId())
                        + ",\"eventId\":" + ReceiverState.esc(h.eventId())
                        + ",\"attempt\":" + ReceiverState.esc(h.attempt())
                        + ",\"status\":" + h.status()
                        + ",\"body\":" + ReceiverState.esc(h.body()) + "}").collect(Collectors.joining(",")) + "]";
    }

    @GetMapping(value = "/inspect/stats", produces = "application/json")
    public String stats() {
        return state.statsJson();
    }

    @PostMapping("/reset")
    public String reset() {
        state.reset();
        return "reset";
    }

    /** Body = the signing secret exactly as Relay showed it, e.g. whsec_AbC... (base64url after the prefix). */
    @PostMapping("/config/secret")
    public String secret(@RequestBody String whsec) {
        String s = whsec.trim();
        if (s.startsWith("whsec_")) {
            s = s.substring("whsec_".length());
        }
        state.secret = Base64.getUrlDecoder().decode(s.getBytes(StandardCharsets.UTF_8));
        return "secret set (" + state.secret.length + " bytes)";
    }

    @PostMapping("/config/switch")
    public String sw(@RequestParam boolean ok) {
        state.switchOk = ok;
        return "switch ok=" + ok;
    }
}
