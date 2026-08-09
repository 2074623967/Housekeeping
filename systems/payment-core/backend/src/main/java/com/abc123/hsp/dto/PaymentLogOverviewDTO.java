package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付处理日志总览指标。
 */
@Data
public class PaymentLogOverviewDTO {

    /** 日志总数。 */
    private Long totalLogCount;
    /** 错误日志数。 */
    private Long errorLogCount;
    /** 告警日志数。 */
    private Long warnLogCount;
    /** 信息日志数。 */
    private Long infoLogCount;
    /** 涉及处理阶段数。 */
    private Integer distinctStageCount;
    /** 涉及来源数。 */
    private Integer distinctSourceCount;
    /** 渠道回调错误数。 */
    private Integer callbackErrorCount;
    /** 业务事件告警数。 */
    private Integer eventWarnCount;
    /** 回调关键词命中数。 */
    private Integer callbackKeywordCount;
    /** 最近一次日志时间。 */
    private String latestLogAt;
}
