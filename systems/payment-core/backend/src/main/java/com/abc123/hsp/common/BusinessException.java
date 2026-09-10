package com.abc123.hsp.common;

/**
 * 支付核心域统一业务异常。
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static BusinessException sanitized(String code, String message) {
        return new BusinessException(code, message, null, false, false);
    }

    private BusinessException(String code, String message, Throwable cause,
                              boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
