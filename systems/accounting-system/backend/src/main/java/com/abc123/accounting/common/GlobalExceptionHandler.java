package com.abc123.accounting.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 账务系统统一异常返回。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
        String message = exception.getMessage() == null ? "system error" : exception.getMessage();
        return ApiResponse.failure(ErrorCode.SYSTEM_ERROR, message);
    }
}
