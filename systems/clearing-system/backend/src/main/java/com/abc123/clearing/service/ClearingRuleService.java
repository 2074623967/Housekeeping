package com.abc123.clearing.service;

import com.abc123.clearing.dto.ClearingRuleDTO;
import com.abc123.clearing.dto.CreateClearingRuleRequestDTO;
import com.abc123.clearing.dto.PageResultDTO;

/**
 * 清分规则服务。
 */
public interface ClearingRuleService {

    PageResultDTO<ClearingRuleDTO> list(String ruleType, String ruleStatus, int pageNo, int pageSize);

    ClearingRuleDTO create(CreateClearingRuleRequestDTO request);

    ClearingRuleDTO enable(String ruleNo);

    ClearingRuleDTO disable(String ruleNo);
}
