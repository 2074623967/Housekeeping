package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议新增或编辑请求。
 */
@Data
public class PaymentProtocolUpsertRequestDTO {

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
    /** 适用渠道范围。 */
    private String channelScope;
    /** 是否要求商户确认。 */
    private String merchantAckRequired;
    /** 风控标签。 */
    private String riskControlTag;
    /** 协议优先级。 */
    private Integer priority;
    /** 是否启用。 */
    private Boolean enabled;
}
