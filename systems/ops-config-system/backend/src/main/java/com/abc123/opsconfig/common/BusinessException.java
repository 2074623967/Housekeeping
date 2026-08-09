package com.abc123.opsconfig.common;

/**
 * 运营配置业务异常。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
