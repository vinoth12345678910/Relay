package com.relay.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Relay: Reliable webhook delivery service.
 * Entry point for the Spring Boot application.
 */
@SpringBootApplication
@EnableScheduling  // For background jobs like the delivery poller
public class RelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelayApplication.class, args);
    }
}