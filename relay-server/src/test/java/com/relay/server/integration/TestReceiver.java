// File: relay-server/src/test/java/com/relay/server/integration/TestReceiver.java
package com.relay.server.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

/**
 * A webhook receiver for tests. Records every request and tracks how many were in flight at once.
 * {@code statusFor} maps the 1-based request number to the HTTP status to return.
 */
class TestReceiver implements AutoCloseable {

    record Hit(int number, Map<String, String> headers, String body, long atMillis) {}

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Hit> hits = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> perEvent = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();

    volatile IntUnaryOperator statusFor = n -> 200;
    volatile long delayMs = 0;
    /** Value for a Retry-After response header by request number; null means no header. */
    volatile java.util.function.IntFunction<String> retryAfterFor = n -> null;

    TestReceiver() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(executor);
        server.createContext("/hook", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    private void handle(HttpExchange ex) throws IOException {
        int n = total.incrementAndGet();
        int now = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(now, Math::max);
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> headers = new ConcurrentHashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            hits.add(new Hit(n, headers, body, System.currentTimeMillis()));
            perEvent.computeIfAbsent(headers.getOrDefault("relay-event-id", "?"), k -> new AtomicInteger()).incrementAndGet();
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            String retryAfter = retryAfterFor.apply(n);
            if (retryAfter != null) {
                ex.getResponseHeaders().add("Retry-After", retryAfter);
            }
            ex.sendResponseHeaders(statusFor.applyAsInt(n), -1);
            ex.close();
            completed.incrementAndGet();
        } catch (InterruptedException | IOException e) {
            // client went away (timeout or shutdown); nothing to answer
        } finally {
            inFlight.decrementAndGet();
        }
    }

    int total() { return total.get(); }
    int inFlight() { return inFlight.get(); }
    int maxInFlight() { return maxInFlight.get(); }
    int completed() { return completed.get(); }
    List<Hit> hits() { return hits; }
    Map<String, AtomicInteger> perEvent() { return perEvent; }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
