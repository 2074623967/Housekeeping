package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付成功事件入账请求。
 */
@Data
public class PaymentSuccessEventRequestDTO {

    private String accountNo;
    private String paymentOrderId;
    private String orderNo;
    private String customerName;
    private BigDecimal amount;
}
