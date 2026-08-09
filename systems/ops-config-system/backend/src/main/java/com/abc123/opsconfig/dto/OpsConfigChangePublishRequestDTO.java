package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 发布运营配置变更请求。
 */
@Data
public class OpsConfigChangePublishRequestDTO {

    /** 变更申请号。 */
    private String requestNo;
    /** 发布人。 */
    private String operator;
}
