package com.abc123.settlement.entity;

import lombok.Data;

/**
 * 审核日志实体。
 */
@Data
public class SettlementAuditLogEntity {

    private String auditNo;
    private String settlementNo;
    private String auditAction;
    private String auditResult;
    private String operatorName;
    private String remark;
    private String createdAt;
}
