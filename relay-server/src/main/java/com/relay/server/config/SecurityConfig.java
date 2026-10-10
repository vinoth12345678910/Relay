// File: relay-server/src/main/java/com/relay/server/config/SecurityConfig.java
package com.relay.server.config;

import com.relay.server.app.AppService;
import com.relay.server.security.AdminTokenFilter;
import com.relay.server.security.ApiKeyAuthFilter;
import com.relay.server.security.CurrentApp;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

/**
 * Stateless API security. Authentication is done by our two filters (admin token for
 * {@code POST /api/v1/apps}, API key for the rest of {@code /api/v1/**}); Spring Security supplies
 * the chain, header hardening and CORS.
 */
@Configuration
public class SecurityConfig {

    /** Builds the chain: stateless, no CSRF, our filters ahead of the default auth filter. */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppService appService,
            CurrentApp currentApp,
            RelayProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(new AdminTokenFilter(properties, exceptionResolver),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ApiKeyAuthFilter(appService, currentApp, exceptionResolver),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Rejects everything. Defining it stops Boot from creating a default user with a
     * generated password that it would print to the log.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new BadCredentialsException("Password authentication is not supported");
        };
    }

    /** Dev only: allow browsers on localhost to call the API. */
    @Bean
    @Profile("dev")
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
