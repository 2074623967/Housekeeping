package com.abc123.hsp.entity;

import lombok.Data;

/** 支付网关不可变版本快照。 */
@Data
public class PaymentGatewayVersionEntity extends PaymentGatewayConfigEntity {

    private Integer versionNo;
    private String changeType;
    private Integer sourceVersionNo;
    private String changeReason;
}
