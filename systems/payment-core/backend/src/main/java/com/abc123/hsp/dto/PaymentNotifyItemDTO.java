package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付回调通知日志行模型。
 */
@Data
public class PaymentNotifyItemDTO {

    /** 通知流水号。 */
    private String notifyNo;
    /** 渠道编码。 */
    private String channelCode;
    /** 通知类型。 */
    private String notifyType;
    /** 通知状态。 */
    private String notifyStatus;
    /** 通知状态样式。 */
    private String notifyStatusType;
    /** 创建时间。 */
    private String createdAt;
}
