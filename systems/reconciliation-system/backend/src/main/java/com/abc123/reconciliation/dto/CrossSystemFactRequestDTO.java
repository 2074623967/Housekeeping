package com.abc123.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 跨系统事实快照上报请求。
 */
@Data
public class CrossSystemFactRequestDTO {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    /** 来源系统编码。 */
    private String systemCode;
    /** 事实类型，例如 PAYMENT_SUCCESS / CLEARING_GENERATED。 */
    private String factType;
    /** 来源系统事实编号。 */
    private String factNo;
    /** 来源系统业务编号。 */
    private String businessNo;
    /** 支付业务场景。 */
    private String payScene;
    /** 事实状态。 */
    private String factStatus;
    /** 事实金额。 */
    private BigDecimal amount;
    /** 来源配置快照版本。 */
    private Long snapshotVersion;
    /** 事实发生时间。 */
    private LocalDateTime occurredAt;
    /** 事实摘要或脱敏载荷。 */
    private String payload;
}
