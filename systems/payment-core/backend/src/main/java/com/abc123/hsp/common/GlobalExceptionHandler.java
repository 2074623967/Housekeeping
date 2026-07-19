package com.abc123.hsp.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        String message = exception.getMessage() == null ? "system error" : exception.getMessage();
        return ApiResponse.failure("500", message);
    }
}
