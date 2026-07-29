package com.abc123.hsp.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 全局异常处理器测试。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void shouldReturnBusinessErrorCodeWhenHandleBusinessException() {
        ApiResponse<Void> response = globalExceptionHandler.handleBusinessException(
                new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND, "支付单不存在")
        );

        Assertions.assertEquals(ErrorCode.PAYMENT_ORDER_NOT_FOUND, response.getCode());
        Assertions.assertEquals("支付单不存在", response.getMessage());
    }

    @Test
    void shouldReturnValidationErrorCodeWhenHandleIllegalArgumentException() {
        ApiResponse<Void> response = globalExceptionHandler.handleIllegalArgumentException(
                new IllegalArgumentException("paymentMethod is required")
        );

        Assertions.assertEquals(ErrorCode.VALIDATION_ERROR, response.getCode());
        Assertions.assertEquals("paymentMethod is required", response.getMessage());
    }

    @Test
    void shouldReturnSystemErrorCodeWhenHandleUnexpectedException() {
        ApiResponse<Void> response = globalExceptionHandler.handleException(
                new IllegalStateException("unexpected system exception")
        );

        Assertions.assertEquals(ErrorCode.SYSTEM_ERROR, response.getCode());
        Assertions.assertEquals("unexpected system exception", response.getMessage());
    }
}
