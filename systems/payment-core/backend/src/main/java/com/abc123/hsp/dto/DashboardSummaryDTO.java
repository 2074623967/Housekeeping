package com.abc123.hsp.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作台汇总数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {

    /** 工作台卡片列表。 */
    private List<DashboardCardDTO> cards;
}
