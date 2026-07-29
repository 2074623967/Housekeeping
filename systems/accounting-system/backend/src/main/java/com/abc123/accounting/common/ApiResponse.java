package com.abc123.accounting.common;

import lombok.Data;

/**
 * 统一接口返回结构。
 *
 * @param <T> 数据体类型
 */
@Data
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        response.setRequestId(RequestIdHolder.nextRequestId());
        return response;
    }
}
