package com.abc123.settlement.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 出款巡检结果。
 */
@Data
public class PayoutInspectionDTO {

    private int totalBatchCount;
    private int pendingBatchCount;
    private int failedBatchCount;
    private int completedBatchCount;
    private List<String> pendingBatchNos = new ArrayList<>();
    private List<String> failedBatchNos = new ArrayList<>();
}
