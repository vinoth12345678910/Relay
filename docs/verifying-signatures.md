# Verifying Relay webhooks

Every delivery is signed so your receiver can prove it came from Relay and was not altered.
**Verify before you trust the body.** Reject anything that fails.

## What Relay sends

| Header | Meaning |
|---|---|
| `Relay-Signature` | `v1=<hex>`; may hold several space/comma separated `v1=` values |
| `Relay-Timestamp` | Unix seconds when the request was signed |
| `Relay-Event-Id` | The event this delivery belongs to |
| `Relay-Delivery-Id` | Stable across retries of the same delivery: **use it to deduplicate** |
| `Relay-Attempt` | 1, 2, 3... counts up across retries (and replays) |

## The algorithm

```
signed_string = "<Relay-Timestamp>" + "." + "<raw request body>"
signature     = hex( HMAC-SHA256( key, signed_string ) )
header        = "v1=" + signature
```

* **`key` is the raw bytes** of your endpoint secret. When you create an endpoint, Relay shows the secret
  **once**, as `whsec_<base64url>`. Strip the `whsec_` prefix and **base64url-decode** the rest. Do not use the
  `whsec_...` string itself as the key.
* **Sign the raw body bytes exactly as received.** If your framework parses the JSON first and you
  re-serialize it, key order and whitespace change and the signature will not match. Read the raw bytes.
* **Compare in constant time** (the samples below do).
* **Check the timestamp** (the samples allow 5 minutes) so a captured request cannot be replayed later.

## Deduplicate: delivery is at-least-once

If your receiver crashes or answers slowly, Relay retries, and in rare cases (a Relay worker dying mid-send)
the same delivery can arrive twice. Store `Relay-Delivery-Id` and ignore repeats. Return a `2xx` quickly; any
other status (including `3xx`, which Relay never follows) counts as a failure and is retried with backoff.

## Reference implementations

The three samples below were each run against a signature computed independently with `openssl`
(a body containing non-ASCII characters and quotes): valid signatures pass; a tampered body, a stale
timestamp and a wrong key fail. The repo's `mock-receiver` module (`/hook/verify`) is a working Java receiver.

### Python

```python
import base64, hashlib, hmac, time

def verify(whsec: str, headers: dict, raw_body: bytes, tolerance_seconds: int = 300) -> bool:
    # The signing key is the base64url-decoded part after "whsec_".
    b64 = whsec.removeprefix("whsec_")
    key = base64.urlsafe_b64decode(b64 + "=" * (-len(b64) % 4))

    timestamp = headers["Relay-Timestamp"]
    if abs(time.time() - int(timestamp)) > tolerance_seconds:
        return False                                   # too old or from the future: possible replay

    signed = timestamp.encode() + b"." + raw_body      # exactly "<timestamp>.<raw body>"
    expected = hmac.new(key, signed, hashlib.sha256).hexdigest()

    # The header may hold several space/comma separated signatures, e.g. "v1=abc v1=def".
    for part in headers["Relay-Signature"].replace(",", " ").split():
        if part.startswith("v1=") and hmac.compare_digest(part[3:], expected):
            return True
    return False
```

### Node.js

```js
const crypto = require("crypto");

function verify(whsec, headers, rawBody /* Buffer */, toleranceSeconds = 300) {
  // The signing key is the base64url-decoded part after "whsec_".
  const key = Buffer.from(whsec.replace(/^whsec_/, ""), "base64url");

  const timestamp = headers["relay-timestamp"];
  if (Math.abs(Date.now() / 1000 - Number(timestamp)) > toleranceSeconds) return false;

  const expected = crypto.createHmac("sha256", key)
    .update(Buffer.concat([Buffer.from(timestamp + "."), rawBody]))   // "<timestamp>.<raw body>"
    .digest("hex");

  return headers["relay-signature"].split(/[\s,]+/).some((part) => {
    if (!part.startsWith("v1=")) return false;
    const got = Buffer.from(part.slice(3));
    const want = Buffer.from(expected);
    return got.length === want.length && crypto.timingSafeEqual(got, want);
  });
}
module.exports = { verify };
```

### Java

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

public final class Verify {
    /** headers: Relay-Timestamp and Relay-Signature values; rawBody: the request body bytes, untouched. */
    public static boolean verify(String whsec, String timestamp, String signatureHeader, byte[] rawBody, long toleranceSeconds)
            throws Exception {
        // The signing key is the base64url-decoded part after "whsec_".
        byte[] key = Base64.getUrlDecoder().decode(whsec.replaceFirst("^whsec_", ""));

        if (Math.abs(System.currentTimeMillis() / 1000 - Long.parseLong(timestamp)) > toleranceSeconds) {
            return false;                                            // too old or from the future
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));   // "<timestamp>.<raw body>"
        mac.update(rawBody);
        byte[] expected = HexFormat.of().formatHex(mac.doFinal()).getBytes(StandardCharsets.UTF_8);

        for (String part : signatureHeader.split("[,\\s]+")) {
            if (part.startsWith("v1=")
                    && MessageDigest.isEqual(expected, part.substring(3).getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }
}
```
