package com.abc123.opsconfig.entity;

import lombok.Data;

/**
 * 业务线实体。
 */
@Data
public class BusinessLineEntity {

    /** 主键。 */
    private Long id;
    /** 业务线编码。 */
    private String businessCode;
    /** 业务线名称。 */
    private String businessName;
    /** 默认支付场景。 */
    private String defaultScene;
    /** 负责人。 */
    private String owner;
    /** 清结算策略。 */
    private String settlementPolicy;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
