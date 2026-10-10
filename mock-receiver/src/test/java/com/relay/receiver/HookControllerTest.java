// File: mock-receiver/src/test/java/com/relay/receiver/HookControllerTest.java
package com.relay.receiver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HookControllerTest {

    private ReceiverState state;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        state = new ReceiverState();
        mvc = MockMvcBuilders.standaloneSetup(new HookController(state), new InspectController(state)).build();
    }

    private MockHttpServletRequestBuilder hook(String path, String deliveryId, String body) {
        return post(path).header("Relay-Delivery-Id", deliveryId).header("Relay-Event-Id", "evt")
                .header("Relay-Attempt", "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void okRecordsTheHit() throws Exception {
        mvc.perform(hook("/hook/ok", "d1", "{}")).andExpect(status().isOk());

        assertThat(state.statsJson()).contains("\"totalRequests\":1", "\"distinctDeliveries\":1");
        mvc.perform(get("/inspect")).andExpect(status().isOk());
    }

    @Test
    void failFirstFailsPerDeliveryThenSucceeds() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(hook("/hook/fail-first/3", "d1", "{}")).andExpect(status().isInternalServerError());
        }
        mvc.perform(hook("/hook/fail-first/3", "d1", "{}")).andExpect(status().isOk());
        mvc.perform(hook("/hook/fail-first/3", "d2", "{}")).andExpect(status().isInternalServerError());   // its own count
    }

    @Test
    void busyAnswers429WithRetryAfterOnlyTheFirstTime() throws Exception {
        mvc.perform(hook("/hook/busy/7", "d1", "{}")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"));
        mvc.perform(hook("/hook/busy/7", "d1", "{}")).andExpect(status().isOk());
    }

    @Test
    void goneAndAlwaysFailAndRedirect() throws Exception {
        mvc.perform(hook("/hook/gone", "d1", "{}")).andExpect(status().isGone());
        mvc.perform(hook("/hook/always-500", "d1", "{}")).andExpect(status().isInternalServerError());
        mvc.perform(hook("/hook/redirect", "d1", "{}")).andExpect(status().isFound())
                .andExpect(header().string("Location", "/hook/redirect-target"));
        assertThat(state.statsJson()).contains("\"redirectTargetHits\":0");
        mvc.perform(post("/hook/redirect-target")).andExpect(status().isOk());
        assertThat(state.statsJson()).contains("\"redirectTargetHits\":1");
    }

    @Test
    void switchFollowsTheConfigFlag() throws Exception {
        mvc.perform(hook("/hook/switch", "d1", "{}")).andExpect(status().isInternalServerError());
        mvc.perform(post("/config/switch").param("ok", "true")).andExpect(status().isOk());
        mvc.perform(hook("/hook/switch", "d2", "{}")).andExpect(status().isOk());
    }

    @Test
    void verifyAcceptsAGoodSignatureAndRejectsBadOnes() throws Exception {
        byte[] key = "reference-secret".getBytes(StandardCharsets.UTF_8);
        String whsec = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        mvc.perform(post("/config/secret").contentType(MediaType.TEXT_PLAIN).content(whsec)).andExpect(status().isOk());
        String ts = Long.toString(Instant.now().getEpochSecond());
        String body = "{\"a\":1}";
        String good = "v1=" + HookController.hmacHex(key, ts + "." + body);

        mvc.perform(hook("/hook/verify", "d1", body).header("Relay-Timestamp", ts).header("Relay-Signature", good))
                .andExpect(status().isOk());
        mvc.perform(hook("/hook/verify", "d2", body + " ").header("Relay-Timestamp", ts).header("Relay-Signature", good))
                .andExpect(status().isUnauthorized());                                       // body tampered
        mvc.perform(hook("/hook/verify", "d3", body).header("Relay-Timestamp", ts).header("Relay-Signature", "v1=00"))
                .andExpect(status().isUnauthorized());
        mvc.perform(hook("/hook/verify", "d4", body).header("Relay-Timestamp", "1").header("Relay-Signature",
                "v1=" + HookController.hmacHex(key, "1." + body))).andExpect(status().isUnauthorized());   // too old
        mvc.perform(hook("/hook/verify", "d5", body)).andExpect(status().isUnauthorized());   // headers missing
    }

    @Test
    void verifyWithoutAConfiguredSecretIsAServerError() throws Exception {
        mvc.perform(hook("/hook/verify", "d1", "{}")).andExpect(status().isInternalServerError());
    }

    @Test
    void statsCountRepeatsAndResetClearsEverything() throws Exception {
        mvc.perform(hook("/hook/ok", "d1", "{}"));
        mvc.perform(hook("/hook/ok", "d1", "{}"));
        mvc.perform(hook("/hook/ok", "d2", "{}"));
        assertThat(state.statsJson()).contains("\"totalRequests\":3", "\"distinctDeliveries\":2", "\"deliveriesWithMultipleHits\":1");

        mvc.perform(post("/reset")).andExpect(status().isOk());

        assertThat(state.statsJson()).contains("\"totalRequests\":0", "\"distinctDeliveries\":0");
    }
}
