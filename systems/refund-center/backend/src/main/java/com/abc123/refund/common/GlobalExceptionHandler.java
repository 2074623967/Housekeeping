package com.abc123.refund.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一转换业务异常，避免前端收到不稳定的异常结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode("REFUND_BUSINESS_ERROR");
        response.setMessage(exception.getMessage());
        response.setRequestId(RequestIdHolder.nextRequestId());
        return response;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode("REFUND_INVALID_ARGUMENT");
        response.setMessage(exception.getMessage());
        response.setRequestId(RequestIdHolder.nextRequestId());
        return response;
    }
}

