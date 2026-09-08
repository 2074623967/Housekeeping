package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码启停复核申请参数。
 */
@Data
public class PaymentReturnCodeStatusReviewRequestDTO {

    private String channelCode;
    private String channelReturnCode;
    private Boolean enabled;
    /** 申请人声明；当前未接入可信身份源。 */
    private String requester;
    private String changeReason;
}
