package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议配置展示对象。
 */
@Data
public class PaymentProtocolConfigDTO {

    /** 协议编码。 */
    private String protocolCode;
    /** 协议名称。 */
    private String protocolName;
    /** 协议类型。 */
    private String protocolType;
    /** 协议模板编码。 */
    private String templateCode;
    /** 协议模板名称。 */
    private String templateName;
    /** 模板版本。 */
    private String templateVersion;
    /** 签约模式。 */
    private String signMode;
    /** 签约要素配置。 */
    private String signElementSpec;
    /** 电子签章服务商。 */
    private String eSignatureProvider;
    /** 适用场景。 */
    private String sceneScope;
    /** 适用渠道。 */
    private String channelScope;
    /** 商户确认要求。 */
    private String merchantAckRequired;
    /** 风控标签。 */
    private String riskControlTag;
    /** 协议优先级。 */
    private Integer priority;
    /** 协议状态。 */
    private String status;
    /** 协议状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
