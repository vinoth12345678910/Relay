package com.relay.receiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Stand-in webhook receiver. Currently just boots; receiving endpoints are added with the
 * end-to-end delivery work.
 */
@SpringBootApplication
public class MockReceiverApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockReceiverApplication.class, args);
    }
}
