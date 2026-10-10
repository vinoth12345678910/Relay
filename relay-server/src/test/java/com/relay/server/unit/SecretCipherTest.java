// File: relay-server/src/test/java/com/relay/server/unit/SecretCipherTest.java
package com.relay.server.unit;

import com.relay.server.config.RelayProperties;
import com.relay.server.security.KeyHasher;
import com.relay.server.security.SecretCipher;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static SecretCipher cipher(String base64Key) {
        RelayProperties props = new RelayProperties();
        props.setSecretEncryptionKey(base64Key);
        return new SecretCipher(props, new SecureRandom());
    }

    private static final String KEY_A = "aW50ZWdyYXRpb24tdGVzdC1rZXktMzItYnl0ZXMhIQ==";
    private static final String KEY_B = "b3RoZXIta2V5LW90aGVyLWtleS1vdGhlci1rZXkhIQ==";

    @Test
    void roundTripsAndUsesAFreshIvEachTime() {
        SecretCipher c = cipher(KEY_A);
        byte[] secret = "super-secret-signing-key".getBytes();

        byte[] one = c.encrypt(secret);
        byte[] two = c.encrypt(secret);

        assertThat(c.decrypt(one)).isEqualTo(secret);
        assertThat(one).isNotEqualTo(two);
        assertThat(new String(one)).doesNotContain("super-secret");
    }

    @Test
    void tamperedOrWrongKeyCiphertextFailsWithoutLeakingAnything() {
        byte[] stored = cipher(KEY_A).encrypt("secret".getBytes());
        byte[] tampered = stored.clone();
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher(KEY_A).decrypt(tampered))
                .isInstanceOf(IllegalStateException.class).hasMessage("Secret decryption failed");
        assertThatThrownBy(() -> cipher(KEY_B).decrypt(stored))
                .isInstanceOf(IllegalStateException.class).hasMessage("Secret decryption failed");
        assertThatThrownBy(() -> cipher(KEY_A).decrypt(new byte[3]))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidBase64KeyFailsFastWithoutEchoingIt() {
        assertThatThrownBy(() -> cipher("%%%not-base64%%%"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("not-base64");
    }

    @Test
    void keyHasherIsDeterministicAndOneWay() {
        KeyHasher h = new KeyHasher();

        assertThat(h.hash("rl_live_abc")).isEqualTo(h.hash("rl_live_abc")).hasSize(64).doesNotContain("rl_live_abc");
        assertThat(h.hash("rl_live_abc")).isNotEqualTo(h.hash("rl_live_abd"));
    }
}
