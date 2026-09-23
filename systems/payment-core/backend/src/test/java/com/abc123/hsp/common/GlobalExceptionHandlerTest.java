package com.abc123.hsp.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
    void shouldReturnValidationErrorCodeWhenRequestBodyIsUnreadable() {
        ApiResponse<Void> response = globalExceptionHandler.handleUnreadableRequestBody(
                new HttpMessageNotReadableException("request body is empty")
        );

        Assertions.assertEquals(ErrorCode.VALIDATION_ERROR, response.getCode());
        Assertions.assertEquals("请求体不能为空且必须是合法 JSON", response.getMessage());
    }

    @Test
    void shouldReturnValidationErrorCodeWhenRequestParameterBindingFails() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "bad-page", Integer.class, "pageNo", null, new IllegalArgumentException("not a number")
        );

        ApiResponse<Void> response = globalExceptionHandler.handleRequestBindingException(exception);

        Assertions.assertEquals(ErrorCode.VALIDATION_ERROR, response.getCode());
        Assertions.assertTrue(response.getMessage().contains("pageNo"));
    }

    @Test
    void shouldReturnSystemErrorCodeWhenHandleUnexpectedException() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ApiResponse<Void> response = globalExceptionHandler.handleException(
                    new IllegalStateException("database password=secret-value at jdbc:mysql://internal-host/payment")
            );

            Assertions.assertEquals(ErrorCode.SYSTEM_ERROR, response.getCode());
            Assertions.assertEquals("system error", response.getMessage());
            Assertions.assertFalse(response.getMessage().contains("secret-value"));
            Assertions.assertNotNull(response.getRequestId());
            Assertions.assertEquals(1, appender.list.size());
            String logMessage = appender.list.get(0).getFormattedMessage();
            Assertions.assertTrue(logMessage.contains(response.getRequestId()));
            Assertions.assertTrue(logMessage.contains(IllegalStateException.class.getName()));
            Assertions.assertFalse(logMessage.contains("secret-value"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
