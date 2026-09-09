package com.abc123.hsp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "validation error" : exception.getMessage();
        return ApiResponse.failure(ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.SYSTEM_ERROR, "system error");
        LOGGER.error("Unhandled payment-core exception, requestId={}, exceptionType={}",
                response.getRequestId(), exception.getClass().getName());
        return response;
    }
}
