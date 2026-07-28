package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 调用方审计日志。
 */
@Data
public class GatewayAuditLogDTO {

    /** 请求流水号。 */
    private String requestId;
    /** 应用编码。 */
    private String appCode;
    /** 网关编码。 */
    private String gatewayCode;
    /** 操作类型。 */
    private String operationType;
    /** 签名算法。 */
    private String signType;
    /** 客户端IP。 */
    private String clientIp;
    /** 结果状态。 */
    private String resultStatus;
    /** 结果状态样式。 */
    private String resultStatusType;
    /** 风险提示。 */
    private String riskHint;
    /** 发生时间。 */
    private String happenedAt;
}
