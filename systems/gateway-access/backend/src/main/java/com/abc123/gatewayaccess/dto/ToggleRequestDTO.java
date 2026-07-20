package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 启停请求。
 */
@Data
public class ToggleRequestDTO {

    private String configCode;
    private Boolean enabled;
}
