package com.abc123.refund.dto;

import lombok.Data;

/**
 * 退款成功事件派发请求。
 */
@Data
public class RefundOutboxDispatchRequestDTO {

    /** 模拟派发结果。SUCCESS 表示派发成功，FAIL 表示派发失败。 */
    private String simulateResult;
    /** 派发备注或失败原因。 */
    private String remark;
}
