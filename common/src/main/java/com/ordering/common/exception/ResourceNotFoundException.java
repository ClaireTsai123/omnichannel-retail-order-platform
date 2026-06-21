package com.ordering.common.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(StatusCode.NOT_FOUND, message);
    }
}
