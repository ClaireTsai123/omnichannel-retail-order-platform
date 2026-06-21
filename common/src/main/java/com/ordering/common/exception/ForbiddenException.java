package com.ordering.common.exception;

public class ForbiddenException extends BusinessException{
    public ForbiddenException(String message) {
        super(StatusCode.FORBIDDEN, message);
    }
}
