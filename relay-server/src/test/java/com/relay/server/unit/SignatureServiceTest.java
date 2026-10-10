package com.relay.server.unit;

import com.relay.server.worker.SignatureService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureServiceTest {

    @Test
    void matchesRfc4231Vector() {
        String hex = SignatureService.hmacHex(
                "Jefe".getBytes(StandardCharsets.UTF_8),
                "what do ya want for nothing?");
        assertThat(hex)
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void signsTimestampDotBody() {
        SignatureService s = new SignatureService();
        byte[] key = "secret".getBytes(StandardCharsets.UTF_8);
        String expected = "v1=" + SignatureService.hmacHex(key, "1700000000.{\"a\":1}");
        assertThat(s.sign(key, 1700000000L, "{\"a\":1}")).isEqualTo(expected);
    }

    @Test
    void tamperedBodyOrTimestampChangesSignature() {
        SignatureService s = new SignatureService();
        byte[] key = "secret".getBytes(StandardCharsets.UTF_8);
        String original = s.sign(key, 1700000000L, "{\"a\":1}");
        assertThat(s.sign(key, 1700000000L, "{\"a\":2}")).isNotEqualTo(original);
        assertThat(s.sign(key, 1700000001L, "{\"a\":1}")).isNotEqualTo(original);
    }
}