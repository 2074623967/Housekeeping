package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 审核日志展示对象。
 */
@Data
public class SettlementAuditLogDTO {

    private String auditNo;
    private String settlementNo;
    private String auditAction;
    private String auditResult;
    private String operatorName;
    private String remark;
    private String createdAt;
}
