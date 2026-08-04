package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 启停请求。
 */
@Data
public class ToggleRequestDTO {

    /** 配置编码。 */
    private String configCode;
    /** 是否启用。 */
    private Boolean enabled;
}
