package com.abc123.opsconfig.entity;

import lombok.Data;

/**
 * 运营配置变更实体，对应 t_ops_config_change。
 */
@Data
public class OpsConfigChangeEntity {

    /** 主键。 */
    private Long id;
    /** 变更申请号。 */
    private String requestNo;
    /** 配置类型。 */
    private String configType;
    /** 配置编码。 */
    private String configCode;
    /** 目标启停状态。 */
    private String targetStatus;
    /** 变更原因。 */
    private String reason;
    /** 灰度范围。 */
    private String grayScope;
    /** 灰度开始时间。 */
    private String grayStartAt;
    /** 灰度结束时间。 */
    private String grayEndAt;
    /** 申请人。 */
    private String applicant;
    /** 审批人。 */
    private String approver;
    /** 审批意见。 */
    private String approvalComment;
    /** 变更状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 创建时间。 */
    private String createdAt;
    /** 审批时间。 */
    private String approvedAt;
    /** 发布时间。 */
    private String publishedAt;
}
