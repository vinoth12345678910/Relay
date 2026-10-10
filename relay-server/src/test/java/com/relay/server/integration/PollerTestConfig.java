// File: relay-server/src/test/java/com/relay/server/integration/PollerTestConfig.java
package com.relay.server.integration;

import com.relay.server.security.SsrfGuard;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.net.InetAddress;
import java.net.URI;

/**
 * Test-only: lets endpoints and the sender use loopback receivers. Production config is unchanged.
 */
@TestConfiguration
class PollerTestConfig {

    @Bean
    @Primary
    SsrfGuard loopbackAllowedSsrfGuard() {
        return new SsrfGuard() {
            @Override
            public InetAddress resolveSafe(URI uri) {
                return InetAddress.getLoopbackAddress();
            }
        };
    }
}
