package com.abc123.clearing.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付成功事件请求。
 */
@Data
public class PaymentSuccessEventRequestDTO {

    private String paymentOrderId;
    private String orderNo;
    private String batchDate;
    private String customerName;
    private String merchantName;
    private String workerName;
    private BigDecimal amount;
}
