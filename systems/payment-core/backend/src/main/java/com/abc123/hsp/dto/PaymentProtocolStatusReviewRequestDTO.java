package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议启停复核申请参数。
 */
@Data
public class PaymentProtocolStatusReviewRequestDTO {

    /** 协议编码。 */
    private String protocolCode;
    /** 是否启用。 */
    private Boolean enabled;
    /** 申请人声明；当前未接入可信身份源。 */
    private String requester;
    /** 申请原因。 */
    private String changeReason;
}
