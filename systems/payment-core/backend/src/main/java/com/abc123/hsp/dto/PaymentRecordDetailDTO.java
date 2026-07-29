package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 支付记录详情对象，聚合原型字段与支付轨迹。
 */
@Data
public class PaymentRecordDetailDTO extends PaymentRecordRowDTO {

    /** 支付方式。 */
    private String paymentMethod;
    /** 最近一次支付尝试的终端。 */
    private String latestTerminal;
    /** 最近一次支付尝试的客户端 IP。 */
    private String latestClientIp;
    /** 最近一次支付尝试的幂等键。 */
    private String latestIdempotencyKey;
    /** 最近一次支付尝试的状态。 */
    private String latestAttemptStatus;
    /** 最近一次支付尝试的状态样式。 */
    private String latestAttemptStatusType;
    /** 最近一次支付请求报文。 */
    private String latestRequestPayload;
    /** 最近一次支付响应报文。 */
    private String latestResponsePayload;
    /** 支付路由轨迹。 */
    private List<String> routeLogs;
    /** 支付回调轨迹。 */
    private List<String> notifyLogs;
    /** 支付事件轨迹。 */
    private List<String> eventLogs;
}
