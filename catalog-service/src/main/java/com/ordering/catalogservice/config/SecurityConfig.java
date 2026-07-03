package com.ordering.catalogservice.config;

import com.ordering.common.security.HeaderAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final HeaderAuthFilter headerAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**")
                        .hasAnyRole("CUSTOMER", "VENDOR", "ADMIN")

                        .requestMatchers(HttpMethod.POST,"/api/catalog/**")
                        .hasAnyRole("VENDOR", "ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/api/catalog/**")
                        .hasAnyRole("VENDOR", "ADMIN")

                        .requestMatchers(HttpMethod.DELETE, "/api/catalog/**")
                        .hasRole("ADMIN")

                        .anyRequest().authenticated())

                .build();
    }
}
