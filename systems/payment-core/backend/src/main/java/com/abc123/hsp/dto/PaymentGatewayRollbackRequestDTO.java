package com.abc123.hsp.dto;

import lombok.Data;

/** 支付网关历史版本回滚申请。 */
@Data
public class PaymentGatewayRollbackRequestDTO {

    private Integer versionNo;
    private String requester;
    private String changeReason;
}
