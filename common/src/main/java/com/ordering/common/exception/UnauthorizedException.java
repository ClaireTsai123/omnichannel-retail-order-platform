package com.ordering.common.exception;

public class UnauthorizedException extends BusinessException{
    public UnauthorizedException(String message) {
        super(StatusCode.UNAUTHORIZED, message);
    }
}
