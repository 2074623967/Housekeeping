package com.abc123.clearing.dto;

import java.util.List;
import lombok.Data;

/**
 * 清分结果详情对象。
 */
@Data
public class ClearingOrderDetailDTO {

    private ClearingOrderDTO order;
    private List<ShareItemDTO> shareItems;
    private List<FeeRuleDTO> feeRules;
}
