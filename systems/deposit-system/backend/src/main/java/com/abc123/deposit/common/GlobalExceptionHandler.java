package com.abc123.deposit.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一转换保证金异常。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handle(BusinessException exception) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode("DEPOSIT_BUSINESS_ERROR");
        response.setMessage(exception.getMessage());
        response.setRequestId("DEPOSIT-REQ-" + System.currentTimeMillis());
        return response;
    }
}
