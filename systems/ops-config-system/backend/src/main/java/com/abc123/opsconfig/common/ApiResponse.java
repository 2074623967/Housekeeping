package com.abc123.opsconfig.common;

import lombok.Data;

/**
 * 统一接口响应。
 */
@Data
public class ApiResponse<T> {

    /** 业务编码。 */
    private String code;
    /** 响应消息。 */
    private String message;
    /** 响应数据。 */
    private T data;
    /** 请求号。 */
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        response.setRequestId("OPS-CONFIG-" + System.currentTimeMillis());
        return response;
    }
}
