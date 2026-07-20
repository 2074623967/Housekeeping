package com.abc123.gatewayaccess.dto;

import java.util.List;
import lombok.Data;

/**
 * 网关接入总览。
 */
@Data
public class GatewayAccessSummaryDTO {

    private List<DashboardMetricDTO> metrics;
    private List<String> highlights;
}
