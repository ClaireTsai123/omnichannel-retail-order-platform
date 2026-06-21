package com.ordering.common.exception;

import com.ordering.common.dto.ApiResponse;
import feign.RetryableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException ex) {
        return ApiResponse.fail(ex.getCode(),ex.getMessage());
    }

    @ExceptionHandler(RetryableException.class)
    public ApiResponse<?> handleRetryable(RetryableException ex) {
        return ApiResponse.fail(
                503,
                "Downstream service unavailable"
        );
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception ex) {
        ex.printStackTrace();
        return ApiResponse.fail(StatusCode.INTERNAL_SERVER_ERROR, "Internal server error");
    }

}
