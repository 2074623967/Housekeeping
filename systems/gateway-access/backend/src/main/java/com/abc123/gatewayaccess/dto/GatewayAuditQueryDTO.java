package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 调用方审计查询条件。
 */
@Data
public class GatewayAuditQueryDTO {

    /** 关键字。 */
    private String keyword;
    /** 应用编码。 */
    private String appCode = "全部";
    /** 结果状态。 */
    private String resultStatus = "全部";
}
