package com.abc123.riskcontrol.common;

/**
 * 风控业务异常。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}

