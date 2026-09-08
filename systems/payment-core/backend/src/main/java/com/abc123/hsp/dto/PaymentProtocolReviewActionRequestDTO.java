package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议复核动作参数。
 */
@Data
public class PaymentProtocolReviewActionRequestDTO {

    /** 复核人声明；当前未接入可信身份源。 */
    private String reviewer;
    /** 复核意见。 */
    private String reviewComment;
}
