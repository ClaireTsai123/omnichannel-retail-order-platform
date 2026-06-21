package com.ordering.orderservice.util;

import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    public synchronized long nextId() {
        long millis = System.currentTimeMillis();
        int random = (int)(Math.random() * 1000);
        return millis * 1000 + random;
    }
}
