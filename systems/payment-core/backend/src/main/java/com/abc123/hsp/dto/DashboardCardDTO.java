package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 工作台指标卡片模型。
 */
@Data
public class DashboardCardDTO {

    /** 卡片标识。 */
    private String key;
    /** 卡片标题。 */
    private String title;
    /** 卡片数值。 */
    private String value;
    /** 徽标样式类型。 */
    private String badgeType;
    /** 徽标文案。 */
    private String badgeText;
}
