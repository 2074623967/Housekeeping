package com.abc123.refund.common;

/**
 * 退款中心业务异常。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}

