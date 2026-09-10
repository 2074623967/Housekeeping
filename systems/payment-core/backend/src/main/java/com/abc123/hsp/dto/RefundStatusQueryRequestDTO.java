package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 退款主动查单请求。
 */
@Data
public class RefundStatusQueryRequestDTO {

    /** 平台退款单号。 */
    private String refundOrderId;
    /** 发起操作人；人工与任务调用均须显式传入。 */
    private String operator;
}
