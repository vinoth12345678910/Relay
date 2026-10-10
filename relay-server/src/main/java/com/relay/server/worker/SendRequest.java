package com.relay.server.worker;

import java.util.UUID;

/**
 * Everything needed to send one webhook.
 * {@link #toString()} is overridden because the default record output would print the signing
 * secret bytes and the payload.
 */
public record SendRequest(
        UUID deliveryId,
        UUID eventId,
        int attemptNumber,
        String url,
        byte[] signingSecret,   // already DECRYPTED by the caller
        String payload          // the exact stored string, never re-serialized
) {

    /** Safe for logs: omits the signing secret and the payload. */
    @Override
    public String toString() {
        return "SendRequest[deliveryId=" + deliveryId + ", eventId=" + eventId
                + ", attemptNumber=" + attemptNumber + ", url=" + url + "]";
    }
}
