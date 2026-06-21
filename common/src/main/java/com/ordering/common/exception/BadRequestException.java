package com.ordering.common.exception;

public class BadRequestException extends BusinessException{
    public BadRequestException(String message) {
        super(StatusCode.INVALID_ARGUMENT, message);
    }
}
