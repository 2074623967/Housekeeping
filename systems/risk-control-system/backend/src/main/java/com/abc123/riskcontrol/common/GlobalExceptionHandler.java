package com.abc123.riskcontrol.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handle(BusinessException exception) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode("RISK_CONTROL_ERROR");
        response.setMessage(exception.getMessage());
        response.setRequestId("RISK-" + System.currentTimeMillis());
        return response;
    }
}

