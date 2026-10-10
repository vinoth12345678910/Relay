package com.relay.server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final RelayProperties props;

    @Bean
    public HttpClient deliveryHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)   // a redirect could point at an internal address
                .connectTimeout(Duration.ofMillis(props.getHttpConnectTimeoutMs()))
                .build();
    }
}