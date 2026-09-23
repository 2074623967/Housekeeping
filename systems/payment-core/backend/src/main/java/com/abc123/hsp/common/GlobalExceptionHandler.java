package com.abc123.hsp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "validation error" : exception.getMessage();
        return ApiResponse.failure(ErrorCode.VALIDATION_ERROR, message);
    }

    /**
     * JSON 请求体为空或无法反序列化时，保持和业务校验一致的错误契约。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadableRequestBody(HttpMessageNotReadableException exception) {
        return ApiResponse.failure(ErrorCode.VALIDATION_ERROR, "请求体不能为空且必须是合法 JSON");
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            MissingServletRequestParameterException.class,
            MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ApiResponse<Void> handleRequestBindingException(Exception exception) {
        if (exception instanceof MethodArgumentTypeMismatchException) {
            MethodArgumentTypeMismatchException mismatch = (MethodArgumentTypeMismatchException) exception;
            return ApiResponse.failure(ErrorCode.VALIDATION_ERROR,
                    "参数 " + mismatch.getName() + " 类型不合法");
        }
        String message = exception.getMessage();
        return ApiResponse.failure(ErrorCode.VALIDATION_ERROR,
                message == null || message.trim().isEmpty() ? "请求参数不合法" : message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.SYSTEM_ERROR, "system error");
        LOGGER.error("Unhandled payment-core exception, requestId={}, exceptionType={}",
                response.getRequestId(), exception.getClass().getName());
        return response;
    }
}
