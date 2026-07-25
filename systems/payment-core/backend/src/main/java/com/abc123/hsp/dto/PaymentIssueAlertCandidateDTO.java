package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付交易异常告警候选项。
 */
@Data
public class PaymentIssueAlertCandidateDTO {

    /** 异常编号。 */
    private String issueNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 异常类型。 */
    private String issueType;
    /** 严重等级。 */
    private String severity;
    /** 责任组。 */
    private String responsibilityGroup;
    /** 接收人。 */
    private String receiver;
    /** 升级接收人。 */
    private String escalationReceiver;
    /** 升级策略说明。 */
    private String escalationPolicy;
    /** 升级超时分钟数。 */
    private Integer escalationTimeoutMinutes;
    /** 班次标签。 */
    private String scheduleTag;
    /** 班次生效时间窗。 */
    private String effectiveWindow;
    /** 告警内容。 */
    private String alertContent;
}
