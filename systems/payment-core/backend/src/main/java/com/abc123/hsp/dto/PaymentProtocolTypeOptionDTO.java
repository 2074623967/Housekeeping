package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议类型字典项。
 */
@Data
public class PaymentProtocolTypeOptionDTO {

    /** 协议类型编码。 */
    private String protocolType;
    /** 协议类型名称。 */
    private String protocolTypeName;
    /** 协议类型说明。 */
    private String description;
}
