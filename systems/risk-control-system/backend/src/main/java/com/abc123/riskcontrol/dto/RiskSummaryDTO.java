package com.abc123.riskcontrol.dto;

import java.util.List;
import lombok.Data;

/**
 * 风控总览。
 */
@Data
public class RiskSummaryDTO {

    /** 看板指标。 */
    private List<DashboardMetricDTO> metrics;
    /** 重点说明。 */
    private List<String> highlights;
}

