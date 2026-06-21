package com.ordering.common.exception;

public class DownstreamServiceException extends BusinessException {

    public DownstreamServiceException(String message) {
        super(StatusCode.INTERNAL_SERVER_ERROR, message);
    }
}
