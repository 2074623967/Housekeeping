package com.abc123.opsconfig.dto;

import java.util.List;
import lombok.Data;

/**
 * 运营配置总览。
 */
@Data
public class OpsConfigSummaryDTO {

    /** 看板指标。 */
    private List<DashboardMetricDTO> metrics;
    /** 重点说明。 */
    private List<String> highlights;
}
