package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 收银台模板视图。
 */
@Data
public class CashierTemplateDTO {

    /** 模板编码。 */
    private String templateCode;
    /** 模板名称。 */
    private String templateName;
    /** 适用终端。 */
    private String terminalType;
    /** 默认支付方式。 */
    private String defaultPayMethod;
    /** 展示策略。 */
    private String displayPolicy;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
