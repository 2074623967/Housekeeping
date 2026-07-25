package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付交易异常告警供应商投递结果。
 */
@Data
public class PaymentIssueAlertDeliveryResultDTO {

    /** 供应商原始回执快照。 */
    private String providerReceiptSnapshot;
    /** 供应商侧回执号。 */
    private String providerReceiptNo;
    /** 供应商侧投递状态。 */
    private String providerDeliveryStatus;
    /** 供应商侧投递说明。 */
    private String providerDeliveryMessage;
    /** 实际渲染后的告警内容快照。 */
    private String renderedContentSnapshot;
}
