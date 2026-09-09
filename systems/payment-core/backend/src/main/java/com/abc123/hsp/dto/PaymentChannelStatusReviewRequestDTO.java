package com.abc123.hsp.dto;

import lombok.Data;

/** 支付渠道启停复核提交请求。 */
@Data
public class PaymentChannelStatusReviewRequestDTO {

    private String channelCode;
    private Boolean enabled;
    private String requester;
    private String changeReason;
}
