package com.abc123.reconciliation.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一转换对账业务异常。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handle(BusinessException exception) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode("RECON_BUSINESS_ERROR");
        response.setMessage(exception.getMessage());
        response.setRequestId("RECON-REQ-" + System.currentTimeMillis());
        return response;
    }
}

