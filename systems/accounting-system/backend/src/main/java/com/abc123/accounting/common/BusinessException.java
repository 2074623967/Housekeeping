package com.abc123.accounting.common;

/**
 * 账务系统业务异常，统一承载错误码和错误文案。
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
