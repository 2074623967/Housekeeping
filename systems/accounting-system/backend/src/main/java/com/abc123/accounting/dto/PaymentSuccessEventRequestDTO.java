package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付成功事件入账请求。
 */
@Data
public class PaymentSuccessEventRequestDTO {

    /** 跨系统业务链路 ID；历史调用未传时按支付单号兼容生成。 */
    private String businessChainId;
    private String accountNo;
    private String paymentOrderId;
    private String orderNo;
    private String customerName;
    private BigDecimal amount;
}
