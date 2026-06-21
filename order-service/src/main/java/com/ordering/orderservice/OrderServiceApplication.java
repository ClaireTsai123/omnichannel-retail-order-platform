package com.ordering.orderservice;
import com.ordering.common.feign.FeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
@SpringBootApplication(
        scanBasePackages = {
                "com.ordering.orderservice",
                "com.ordering.common"
        }
)
@EnableDiscoveryClient
@EnableFeignClients(
        basePackages = "com.ordering",
        defaultConfiguration = FeignConfig.class
)
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
