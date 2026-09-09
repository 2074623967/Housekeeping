package com.abc123.hsp.dto;

import lombok.Data;

/** 异常告警值班路由复核处理请求。 */
@Data
public class PaymentIssueDutyRosterReviewActionRequestDTO {

    private String reviewer;
    private String reviewComment;
}
