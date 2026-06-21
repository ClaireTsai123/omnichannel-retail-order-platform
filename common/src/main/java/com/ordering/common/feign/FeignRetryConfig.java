package com.ordering.common.feign;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignRetryConfig {

    @Bean
    public Retryer feignRetryer() {
        // period = 100ms, maxPeriod = 1s, maxAttempts = 3
        return new Retryer.Default(100, 1000, 3);
    }
}
