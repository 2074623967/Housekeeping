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
    /** 各系统事实明细。 */
    private List<CrossSystemFactDTO> facts;
}
