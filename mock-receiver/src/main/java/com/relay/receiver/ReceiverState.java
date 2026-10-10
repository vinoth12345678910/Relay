// File: mock-receiver/src/main/java/com/relay/receiver/ReceiverState.java
package com.relay.receiver;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Everything the mock receiver remembers, in memory. It is what the chaos demo reads to prove
 * delivery behaviour: how many requests arrived, whether any delivery was sent twice, whether the
 * same delivery was ever in flight twice at once, and whether a redirect was followed.
 */
@Component
public class ReceiverState {

    public record Hit(Instant at, String path, String deliveryId, String eventId,
                      String attempt, int status, String body) {}

    private static final int MAX_KEPT = 5000;

    private final Deque<Hit> hits = new ArrayDeque<>();
    private final ConcurrentHashMap<String, AtomicInteger> perDelivery = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> inFlightPerDelivery = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final AtomicInteger overlaps = new AtomicInteger();   // same delivery arriving while still in flight
    private final AtomicInteger redirectTargetHits = new AtomicInteger();

    /** Controls /hook/switch. */
    public volatile boolean switchOk = false;
    /** The signing secret bytes /hook/verify checks against. */
    public volatile byte[] secret = null;

    public void enter(String deliveryId) {
        int now = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(now, Math::max);
        if (deliveryId != null
                && inFlightPerDelivery.computeIfAbsent(deliveryId, k -> new AtomicInteger()).incrementAndGet() > 1) {
            overlaps.incrementAndGet();
        }
    }

    public void leave(String deliveryId) {
        inFlight.decrementAndGet();
        if (deliveryId != null) {
            AtomicInteger a = inFlightPerDelivery.get(deliveryId);
            if (a != null) {
                a.decrementAndGet();
            }
        }
    }

    /** How many requests for this delivery were already recorded before the current one. */
    public int priorHits(String deliveryId) {
        AtomicInteger a = deliveryId == null ? null : perDelivery.get(deliveryId);
        return a == null ? 0 : a.get();
    }

    public synchronized void record(Hit h) {
        total.incrementAndGet();
        if (h.deliveryId() != null) {
            perDelivery.computeIfAbsent(h.deliveryId(), k -> new AtomicInteger()).incrementAndGet();
        }
        hits.addLast(h);
        while (hits.size() > MAX_KEPT) {
            hits.removeFirst();
        }
    }

    public void redirectTargetHit() {
        redirectTargetHits.incrementAndGet();
    }

    public synchronized List<Hit> latest(int n) {
        List<Hit> all = new ArrayList<>(hits);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    public String statsJson() {
        long multi = perDelivery.values().stream().filter(a -> a.get() > 1).count();
        return "{\"totalRequests\":" + total.get()
                + ",\"distinctDeliveries\":" + perDelivery.size()
                + ",\"deliveriesWithMultipleHits\":" + multi
                + ",\"maxConcurrent\":" + maxInFlight.get()
                + ",\"overlappingSameDelivery\":" + overlaps.get()
                + ",\"redirectTargetHits\":" + redirectTargetHits.get() + "}";
    }

    public synchronized void reset() {
        hits.clear();
        perDelivery.clear();
        inFlightPerDelivery.clear();
        total.set(0);
        inFlight.set(0);
        maxInFlight.set(0);
        overlaps.set(0);
        redirectTargetHits.set(0);
    }

    /** Minimal JSON string escaping, so this module needs no JSON library. */
    public static String esc(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c < 0x20 ? " " : String.valueOf(c));
            }
        }
        return b.append('"').toString();
    }

    public static String bodyOf(byte[] body) {
        if (body == null) {
            return "";
        }
        String s = new String(body, StandardCharsets.UTF_8);
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
