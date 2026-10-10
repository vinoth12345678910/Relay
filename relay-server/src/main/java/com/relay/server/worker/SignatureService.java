package com.relay.server.worker;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Pure: same inputs always give the same output. Signs "timestamp.body". */
@Component
public class SignatureService {

    public String sign(byte[] secret, long timestampSeconds, String body) {
        return "v1=" + hmacHex(secret, timestampSeconds + "." + body);
    }

    public static String hmacHex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}