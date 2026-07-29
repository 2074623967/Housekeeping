package com.abc123.gatewayaccess.common;

/**
 * 统一接口返回。
 */
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = "0";
        response.message = "success";
        response.data = data;
        response.requestId = RequestIdHolder.requestId();
        return response;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getRequestId() {
        return requestId;
    }
}
