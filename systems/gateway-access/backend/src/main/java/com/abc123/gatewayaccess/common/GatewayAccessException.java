package com.abc123.gatewayaccess.common;

/**
 * 可对调用方公开的网关接入业务异常。
 */
public class GatewayAccessException extends RuntimeException {

    private final String code;

    public GatewayAccessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
