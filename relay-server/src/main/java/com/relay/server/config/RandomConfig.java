// File: relay-server/src/main/java/com/relay/server/config/RandomConfig.java
package com.relay.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Provides the injectable random source. Production uses {@link SecureRandom};
 * tests can replace the bean with a seeded Random for determinism.
 */
@Configuration
public class RandomConfig {

    @Bean
    public Random random() {
        return new SecureRandom();
    }
}
