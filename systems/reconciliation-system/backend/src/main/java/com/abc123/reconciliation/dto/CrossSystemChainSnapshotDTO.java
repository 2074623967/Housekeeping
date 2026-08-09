package com.abc123.reconciliation.dto;

import java.util.List;
import lombok.Data;

/**
 * 跨系统业务链路事实快照。
 */
@Data
public class CrossSystemChainSnapshotDTO {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    /** 已接入事实数。 */
    private int factCount;
    /** 是否存在异常事实。 */
    private boolean hasAbnormalFact;
    /** 当前链路诊断结论。 */
    private String diagnosis;
    /** 链路状态：WAITING、IN_PROGRESS、EXCEPTION、COMPLETED。 */
    private String chainStatus;
    /** 责任状态：UNASSIGNED、PENDING、MANUAL_REVIEW、CLOSED。 */
    private String responsibilityStatus;
    /** 当前建议的下一动作。 */
    private String nextAction;
    /** 异常事实数量。 */
    private int abnormalFactCount;
    /** 已完成的关键阶段数。 */
    private int completedStageCount;
    /** 关键阶段总数。 */
    private int expectedStageCount;
    /** 是否允许进入财务收口。 */
    private boolean financialCloseable;
    /** 最近一条事实发生时间。 */
    private java.time.LocalDateTime lastFactAt;
    /** 各系统事实明细。 */
    private List<CrossSystemFactDTO> facts;
}
