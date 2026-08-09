package com.abc123.clearing.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付成功事件请求。
 */
@Data
public class PaymentSuccessEventRequestDTO {

    /** 跨系统业务链路 ID；历史调用未传时由清分系统按支付单号兼容生成。 */
    private String businessChainId;
    private String paymentOrderId;
    private String orderNo;
    private String batchDate;
    private String customerName;
    private String merchantName;
    private String workerName;
    private BigDecimal amount;
}
