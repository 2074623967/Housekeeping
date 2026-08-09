package com.abc123.opsconfig.entity;

import lombok.Data;

/**
 * 支付类型实体。
 */
@Data
public class PaymentTypeEntity {

    /** 主键。 */
    private Long id;
    /** 类型编码。 */
    private String typeCode;
    /** 类型名称。 */
    private String typeName;
    /** 交易大类。 */
    private String transactionCategory;
    /** 计费口径。 */
    private String feePolicy;
    /** 退款能力。 */
    private String refundCapability;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
