package com.abc123.hsp.dto;

import lombok.Data;

/** 异常告警值班路由启停候选提交请求。 */
@Data
public class PaymentIssueDutyRosterStatusReviewRequestDTO {

    private String rosterCode;
    private Boolean enabled;
    private String requester;
    private String changeReason;
}
