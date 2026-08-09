package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 看板指标。
 */
@Data
public class DashboardMetricDTO {

    /** 指标标题。 */
    private String title;
    /** 指标值。 */
    private String value;
    /** 标签样式。 */
    private String badgeType;
    /** 标签文案。 */
    private String badgeText;
}
