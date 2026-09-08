package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议复核申请提交参数。
 */
@Data
public class PaymentProtocolReviewSubmitRequestDTO {

    /** 候选协议快照。 */
    private PaymentProtocolUpsertRequestDTO candidate;
    /** 申请人声明；当前未接入可信身份源。 */
    private String requester;
}
