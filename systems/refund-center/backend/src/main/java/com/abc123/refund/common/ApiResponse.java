package com.abc123.refund.common;

import lombok.Data;

/**
 * 统一接口返回结构。
 *
 * @param <T> 返回数据类型
 */
@Data
public class ApiResponse<T> {

    /** 业务响应编码，0 表示成功。 */
    private String code;
    /** 响应消息。 */
    private String message;
    /** 响应数据。 */
    private T data;
    /** 请求追踪号。 */
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

