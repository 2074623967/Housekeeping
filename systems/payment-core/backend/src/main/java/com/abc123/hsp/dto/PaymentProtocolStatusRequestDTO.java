package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议启停请求。
 */
@Data
public class PaymentProtocolStatusRequestDTO {

    /** 协议编码。 */
    private String protocolCode;
    /** 是否启用。 */
    private Boolean enabled;
    /** 变更原因。 */
    private String changeReason;
}
