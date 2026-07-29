package com.abc123.gatewayaccess.dto;

import java.util.List;
import lombok.Data;

/**
 * 网关接入总览。
 */
@Data
public class GatewayAccessSummaryDTO {

    /** 总览指标列表。 */
    private List<DashboardMetricDTO> metrics;
    /** 总览说明列表。 */
    private List<String> highlights;
}
