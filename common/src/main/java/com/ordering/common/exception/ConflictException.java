package com.ordering.common.exception;

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(StatusCode.CONFLICT, message);
    }
}
