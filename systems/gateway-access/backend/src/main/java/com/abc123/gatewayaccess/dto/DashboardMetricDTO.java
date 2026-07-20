package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 仪表盘指标。
 */
@Data
public class DashboardMetricDTO {

    private String title;
    private String value;
    private String badgeType;
    private String badgeText;
}
