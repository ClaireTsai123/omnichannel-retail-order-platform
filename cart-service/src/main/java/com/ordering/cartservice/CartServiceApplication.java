package com.ordering.cartservice;

import com.ordering.common.feign.FeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.ordering.cartservice",
        "com.ordering.common"
})
@EnableFeignClients(basePackages = "com.ordering", defaultConfiguration = FeignConfig.class)
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
