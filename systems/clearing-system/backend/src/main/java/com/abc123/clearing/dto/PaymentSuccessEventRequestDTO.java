package com.abc123.clearing.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付成功事件请求。
 */
@Data
public class PaymentSuccessEventRequestDTO {

    /** payment-core 出站事件号，用于跨系统排障和幂等追踪。 */
    private String eventNo;
    /** 事件类型，当前固定为 PAYMENT_SUCCESS。 */
    private String eventType;
    /** 事件载荷契约版本。 */
    private String schemaVersion;
    /** 跨系统业务链路 ID；历史调用未传时由清分系统按支付单号兼容生成。 */
    private String businessChainId;
    private String paymentOrderId;
    /** 支付业务场景。 */
    private String payScene;
    private String orderNo;
    private String batchDate;
    private String customerName;
    private String merchantName;
    private String workerName;
    private BigDecimal amount;
}
