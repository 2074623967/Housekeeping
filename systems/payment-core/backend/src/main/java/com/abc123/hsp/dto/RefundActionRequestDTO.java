package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 退款单运营动作请求。
 */
@Data
public class RefundActionRequestDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 操作备注，例如审核意见、失败原因或重试原因。 */
    private String remark;
}
