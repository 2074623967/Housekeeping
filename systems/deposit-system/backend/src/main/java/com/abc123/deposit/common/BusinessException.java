package com.abc123.deposit.common;

/**
 * 保证金业务异常。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
