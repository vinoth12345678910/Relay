package com.relay.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated configuration for Relay.
 * All RELAY_* environment variables map here.
 * Starts with "relay" because of @ConfigurationProperties("relay").
 */
@Component
@Validated
@ConfigurationProperties(prefix = "relay")
public class RelayProperties {

    private static final int LEASE_MARGIN_SECONDS = 5;

    // Admin & Security
    @NotBlank(message = "RELAY_ADMIN_TOKEN must be set")
    private String adminToken;
    @NotBlank(message = "RELAY_SECRET_ENCRYPTION_KEY must be set (base64)")
    private String secretEncryptionKey; // base64 encoded

    // Worker
    @Min(value = 1, message = "relay.worker-pool-size must be at least 1")
    private Integer workerPoolSize = 5;
    @Min(value = 1, message = "relay.batch-size must be at least 1")
    private Integer batchSize = 10;
    @Min(value = 10, message = "relay.poll-interval-ms must be at least 10")
    private Integer pollIntervalMs = 1000;
    /** Max sends in flight to one endpoint at once, per instance. Keeps one slow receiver from hogging every worker. */
    @Min(value = 1, message = "relay.per-endpoint-max-concurrency must be at least 1")
    private Integer perEndpointMaxConcurrency = 4;
    /** relay.worker.enabled and relay.worker.shutdown-timeout-seconds. */
    @Valid
    private Worker worker = new Worker();

    // Lease
    private Integer leaseSeconds = 30;

    // HTTP
    private Integer httpConnectTimeoutMs = 3000;
    private Integer httpTotalTimeoutMs = 5000;

    // Retry
    private String retrySchedule = "10,30,120,600,1800,7200,21600,43200";
    /** Upper bound on a receiver's Retry-After, so it cannot park a delivery indefinitely. */
    @Min(value = 1, message = "relay.retry-after-max-seconds must be at least 1")
    private Integer retryAfterMaxSeconds = 3600;

    // Circuit Breaker
    private Integer breakerThreshold = 20;

    // Retention
    private Integer retentionDays = 30;

    /**
     * A lease shorter than a send could take lets a second worker re-claim a delivery that is
     * still being sent, causing duplicate webhooks. Checked at startup.
     */
    @AssertTrue(message = "relay.lease-seconds must be greater than relay.http-total-timeout-ms / 1000 + 5 "
            + "(a 5 second margin), otherwise a slow send can outlive its lease and be delivered twice")
    public boolean isLeaseLongerThanHttpTimeout() {
        if (leaseSeconds == null || httpTotalTimeoutMs == null) {
            return true;   // missing values are reported by their own constraints
        }
        return leaseSeconds > httpTotalTimeoutMs / 1000.0 + LEASE_MARGIN_SECONDS;
    }

    /** relay.ssrf.allow-private-networks. */
    @Valid
    private Ssrf ssrf = new Ssrf();

    /** Outbound URL safety switches. */
    public static class Ssrf {
        /**
         * Lets endpoints point at private networks (10/8, 172.16/12, 192.168/16, 100.64/10, fc00::/7).
         * For local and demo setups where the receiver is another container. Loopback, link-local
         * (including the 169.254.169.254 cloud metadata address), unspecified and multicast addresses
         * stay blocked. Never enable this on a deployment that can reach internal services.
         */
        private boolean allowPrivateNetworks = false;

        public boolean isAllowPrivateNetworks() {
            return allowPrivateNetworks;
        }

        public void setAllowPrivateNetworks(boolean allowPrivateNetworks) {
            this.allowPrivateNetworks = allowPrivateNetworks;
        }
    }

    /** Poller switches. */
    public static class Worker {
        /** Set false to run without the delivery poller (tests, API-only nodes). */
        private boolean enabled = true;
        /** How long a graceful stop waits for in-flight sends before interrupting them. */
        @Min(value = 1, message = "relay.worker.shutdown-timeout-seconds must be at least 1")
        private int shutdownTimeoutSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getShutdownTimeoutSeconds() {
            return shutdownTimeoutSeconds;
        }

        public void setShutdownTimeoutSeconds(int shutdownTimeoutSeconds) {
            this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        }
    }

    /** A per-endpoint cap above the pool size could never bind, which is almost certainly a misconfiguration. */
    @AssertTrue(message = "relay.per-endpoint-max-concurrency must not exceed relay.worker-pool-size")
    public boolean isPerEndpointCapWithinWorkerPool() {
        if (perEndpointMaxConcurrency == null || workerPoolSize == null) {
            return true;
        }
        return perEndpointMaxConcurrency <= workerPoolSize;
    }

    // Getters and setters
    public Integer getPerEndpointMaxConcurrency() {
        return perEndpointMaxConcurrency;
    }

    public void setPerEndpointMaxConcurrency(Integer perEndpointMaxConcurrency) {
        this.perEndpointMaxConcurrency = perEndpointMaxConcurrency;
    }

    public Ssrf getSsrf() {
        return ssrf;
    }

    public void setSsrf(Ssrf ssrf) {
        this.ssrf = ssrf;
    }

    public Worker getWorker() {
        return worker;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public String getSecretEncryptionKey() {
        return secretEncryptionKey;
    }

    public void setSecretEncryptionKey(String secretEncryptionKey) {
        this.secretEncryptionKey = secretEncryptionKey;
    }

    public Integer getWorkerPoolSize() {
        return workerPoolSize;
    }

    public void setWorkerPoolSize(Integer workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Integer getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(Integer pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public Integer getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(Integer leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public Integer getHttpConnectTimeoutMs() {
        return httpConnectTimeoutMs;
    }

    public void setHttpConnectTimeoutMs(Integer httpConnectTimeoutMs) {
        this.httpConnectTimeoutMs = httpConnectTimeoutMs;
    }

    public Integer getHttpTotalTimeoutMs() {
        return httpTotalTimeoutMs;
    }

    public void setHttpTotalTimeoutMs(Integer httpTotalTimeoutMs) {
        this.httpTotalTimeoutMs = httpTotalTimeoutMs;
    }

    public String getRetrySchedule() {
        return retrySchedule;
    }

    public void setRetrySchedule(String retrySchedule) {
        this.retrySchedule = retrySchedule;
    }

    public Integer getRetryAfterMaxSeconds() {
        return retryAfterMaxSeconds;
    }

    public void setRetryAfterMaxSeconds(Integer retryAfterMaxSeconds) {
        this.retryAfterMaxSeconds = retryAfterMaxSeconds;
    }

    public Integer getBreakerThreshold() {
        return breakerThreshold;
    }

    public void setBreakerThreshold(Integer breakerThreshold) {
        this.breakerThreshold = breakerThreshold;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }
}