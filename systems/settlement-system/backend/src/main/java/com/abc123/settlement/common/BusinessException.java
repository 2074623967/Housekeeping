package com.abc123.settlement.common;

/**
 * 结算系统业务异常，统一承载可返回前端的错误码和错误文案。
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
