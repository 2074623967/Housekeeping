package com.abc123.refund.dto;

import lombok.Data;

/**
 * 退款审核、提交和重试动作请求。
 */
@Data
public class RefundActionRequestDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 操作备注。 */
    private String remark;
}

