package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 路由执行时使用的支付渠道配置。
 */
@Data
public class PaymentChannelRoutingConfigDTO {

    /** 渠道编码。 */
    private String channelCode;
    /** 支付方式。 */
    private String paymentMethod;
    /** 适用场景。 */
    private String sceneScope;
    /** 单日限额。 */
    private BigDecimal dailyLimit;
    /** 优先级。 */
    private Integer priority;
}
