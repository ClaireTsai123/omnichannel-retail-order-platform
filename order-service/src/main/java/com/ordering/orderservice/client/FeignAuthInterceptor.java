package com.ordering.orderservice.client;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignAuthInterceptor {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes  atts =
                    (ServletRequestAttributes)  RequestContextHolder.getRequestAttributes();
            if (atts == null) return;
            HttpServletRequest request = atts.getRequest();
            String userId = request.getHeader("X-User-Id");
            String role = request.getHeader("X-User-Role");
            String auth = request.getHeader("Authorization");

            if (userId != null) {
                requestTemplate.header("X-User-Id", userId);
            }
            if (role != null) {
                requestTemplate.header("X-User-Role", role);
            }
            if (auth != null) {
                //requestTemplate.header("Authorization");
                requestTemplate.header("Authorization", auth);
            }
        };
    }
}
