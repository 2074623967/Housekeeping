package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 审核结算单请求。
 */
@Data
public class AuditSettlementRequestDTO {

    private String operatorName;
    private String auditRemark;
}
