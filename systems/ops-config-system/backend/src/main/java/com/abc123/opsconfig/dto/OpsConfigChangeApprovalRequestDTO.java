package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 审批运营配置变更请求。
 */
@Data
public class OpsConfigChangeApprovalRequestDTO {

    /** 变更申请号。 */
    private String requestNo;
    /** 是否审批通过。 */
    private Boolean approved;
    /** 审批人。 */
    private String approver;
    /** 审批意见。 */
    private String approvalComment;
}
