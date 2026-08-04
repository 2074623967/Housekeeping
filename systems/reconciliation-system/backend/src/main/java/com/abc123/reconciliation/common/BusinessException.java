package com.abc123.reconciliation.common;

/**
 * 对账业务异常。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}

