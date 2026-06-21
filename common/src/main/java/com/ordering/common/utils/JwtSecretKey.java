package com.ordering.common.utils;

import org.springframework.stereotype.Component;

@Component
public class JwtSecretKey {
    public static final String SECRET_KEY = "#MySecretKeyForJWTTokenGenerationMustBeLongEnough$" ;

}
