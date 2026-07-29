package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付控制策略自检条目结果。
 */
@Data
public class PaymentControlPolicySelfCheckItemDTO {

    /** 来源应用标识。 */
    private String sourceAppId;
    /** 自检状态。 */
    private String selfCheckStatus;
    /** 自检状态样式。 */
    private String selfCheckStatusType;
    /** 自检提示。 */
    private String selfCheckMessage;
}
