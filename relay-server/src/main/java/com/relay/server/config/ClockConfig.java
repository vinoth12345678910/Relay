package com.relay.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides an injectable Clock bean.
 * All business logic uses this instead of Instant.now() or System.currentTimeMillis().
 * This makes tests deterministic (we can mock the clock).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}