package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 工作台卡片实体，对应表：t_dashboard_card。
 */
@Data
public class DashboardCardEntity {

    /** 卡片业务编码。 */
    private String key;
    /** 卡片标题。 */
    private String title;
    /** 卡片展示值。 */
    private String value;
    /** 角标样式类型。 */
    private String badgeType;
    /** 角标文案。 */
    private String badgeText;
}
