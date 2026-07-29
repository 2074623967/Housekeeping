package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付配置启停请求。
 */
@Data
public class PaymentConfigToggleRequestDTO {

    /** 配置编码，渠道编码、路由规则编码或协议编码。 */
    private String configCode;
    /** 是否启用。 */
    private Boolean enabled;
    /** 关联子编码，例如渠道返回码。 */
    private String subCode;
}
