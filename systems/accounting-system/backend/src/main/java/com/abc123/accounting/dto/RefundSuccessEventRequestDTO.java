package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 退款成功事件冲回请求。
 */
@Data
public class RefundSuccessEventRequestDTO {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    /** 待冲回的账务账户。 */
    private String accountNo;
    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 退款金额。 */
    private BigDecimal amount;
    /** 事件摘要。 */
    private String summary;
}
