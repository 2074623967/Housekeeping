package com.abc123.hsp.dto;

import lombok.Data;

/** 异常告警通知供应商启停候选请求。 */
@Data
public class PaymentAlertProviderStatusReviewRequestDTO {

    private String providerCode;
    private Boolean enabled;
    private String requester;
    private String changeReason;
}
