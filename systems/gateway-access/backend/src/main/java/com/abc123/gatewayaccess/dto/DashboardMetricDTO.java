package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 仪表盘指标。
 */
@Data
public class DashboardMetricDTO {

    /** 指标标题。 */
    private String title;
    /** 指标值。 */
    private String value;
    /** 指标标签样式。 */
    private String badgeType;
    /** 指标标签文案。 */
    private String badgeText;
}
