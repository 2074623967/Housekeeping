package com.abc123.opsconfig.common;

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
        response.setCode("OPS_CONFIG_ERROR");
        response.setMessage(exception.getMessage());
        response.setRequestId("OPS-CONFIG-" + System.currentTimeMillis());
        return response;
    }
}
