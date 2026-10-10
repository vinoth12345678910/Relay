// File: relay-server/src/main/java/com/relay/server/config/BackoffConfig.java
package com.relay.server.config;

import com.relay.server.worker.BackoffPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the retry policy from {@code relay.retry-schedule} (seconds) with 20% jitter.
 */
@Configuration
public class BackoffConfig {

    private static final double JITTER_FRACTION = 0.2;

    @Bean
    public BackoffPolicy backoffPolicy(RelayProperties properties) {
        return BackoffPolicy.fromSeconds(properties.getRetrySchedule(), JITTER_FRACTION);
    }
}
