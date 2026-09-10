package com.abc123.gatewayaccess.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将内部异常转换为稳定且不泄露上游细节的响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GatewayAccessException.class)
    public ApiResponse<Void> handleGatewayAccessException(GatewayAccessException exception) {
        return ApiResponse.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ApiResponse.failure("GATEWAY_INVALID_ARGUMENT", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        ApiResponse<Void> response = ApiResponse.failure("GATEWAY_SYSTEM_ERROR", "gateway-access system error");
        LOGGER.error("Unhandled gateway-access exception, requestId={}, exceptionType={}",
                response.getRequestId(), exception.getClass().getName());
        return response;
    }
}
