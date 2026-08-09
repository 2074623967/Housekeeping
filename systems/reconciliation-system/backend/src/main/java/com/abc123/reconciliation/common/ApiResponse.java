package com.abc123.reconciliation.common;

import lombok.Data;

/**
 * 统一接口响应。
 *
 * @param <T> 数据类型
 */
@Data
public class ApiResponse<T> {

    /** 业务编码。 */
    private String code;
    /** 响应消息。 */
    private String message;
    /** 数据体。 */
    private T data;
    /** 请求追踪号。 */
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        response.setRequestId("RECON-REQ-" + System.currentTimeMillis());
        return response;
    }
}

