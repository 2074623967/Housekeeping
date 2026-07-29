package com.abc123.wallet.common;

import lombok.Data;

@Data
public class ApiResponse<T> {
    /** 响应码。 */
    private String code;
    /** 响应消息。 */
    private String message;
    /** 响应数据。 */
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        return response;
    }
}
