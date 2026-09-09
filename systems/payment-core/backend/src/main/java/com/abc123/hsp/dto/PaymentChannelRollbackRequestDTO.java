package com.abc123.hsp.dto;

import lombok.Data;

/** 支付渠道历史版本回滚请求。 */
@Data
public class PaymentChannelRollbackRequestDTO {

    private Integer versionNo;
    /** 申请人声明；当前未接入可信身份源。 */
    private String requester;
    private String changeReason;
}
