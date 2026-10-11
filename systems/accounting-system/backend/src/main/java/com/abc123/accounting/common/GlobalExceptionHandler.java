package com.abc123.accounting.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 账务系统统一异常返回。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        LOGGER.warn("账务业务请求拒绝 category={}",
                ErrorCode.BALANCE_UPDATE_CONFLICT.equals(exception.getCode()) ? "BALANCE_UPDATE_CONFLICT"
                        : ErrorCode.ACCOUNT_NOT_FOUND.equals(exception.getCode()) ? "ACCOUNT_NOT_FOUND" : "OTHER");
        return ApiResponse.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        LOGGER.warn("账务请求参数拒绝 category=VALIDATION_ERROR");
        String message = exception.getMessage() == null ? "validation error" : exception.getMessage();
        return ApiResponse.failure(ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        LOGGER.warn("账务请求执行失败 exceptionType={}", exception.getClass().getSimpleName());
        String message = exception.getMessage() == null ? "system error" : exception.getMessage();
        return ApiResponse.failure(ErrorCode.SYSTEM_ERROR, message);
    }
}
