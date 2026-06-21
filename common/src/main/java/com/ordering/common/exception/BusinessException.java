package com.ordering.common.exception;

import lombok.Getter;

@Getter
public abstract class BusinessException extends RuntimeException {
    private final int code;

    protected BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
