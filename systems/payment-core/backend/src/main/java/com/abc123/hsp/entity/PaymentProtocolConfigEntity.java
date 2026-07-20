package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 支付协议配置实体，对应表：t_payment_protocol_config。
 */
@Data
public class PaymentProtocolConfigEntity {

    /** 协议编码。 */
    private String protocolCode;
    /** 协议名称。 */
    private String protocolName;
    /** 协议类型。 */
    private String protocolType;
    /** 模板版本。 */
    private String templateVersion;
    /** 签约模式。 */
    private String signMode;
    /** 适用场景。 */
    private String sceneScope;
    /** 适用渠道范围。 */
    private String channelScope;
    /** 是否要求商户确认。 */
    private String merchantAckRequired;
    /** 风控标签。 */
    private String riskControlTag;
    /** 状态。 */
    private String status;
    /** 状态样式类型。 */
    private String statusType;
    /** 优先级。 */
    private Integer priority;
}
