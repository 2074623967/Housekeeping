package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 支付交易异常批量处理请求。
 */
@Data
public class PaymentIssueActionRequestDTO {

    /** 待处理异常编号列表。 */
    private List<String> issueNos;
    /** 处理动作类型。 */
    private String actionType;
    /** 当前处理人。 */
    private String assignee;
    /** 操作人。 */
    private String operator;
    /** 处理备注。 */
    private String remark;
}
