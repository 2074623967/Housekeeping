package com.abc123.clearing.service;

import com.abc123.clearing.dto.CreateFeeRuleRequestDTO;
import com.abc123.clearing.dto.FeeRuleDTO;
import com.abc123.clearing.dto.PageResultDTO;

/**
 * 费用规则服务。
 */
public interface FeeRuleService {

    PageResultDTO<FeeRuleDTO> list(String feeType, String status, int pageNo, int pageSize);

    FeeRuleDTO create(CreateFeeRuleRequestDTO request);
}
