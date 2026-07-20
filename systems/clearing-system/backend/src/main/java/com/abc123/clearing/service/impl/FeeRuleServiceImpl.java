package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.CreateFeeRuleRequestDTO;
import com.abc123.clearing.dto.FeeRuleDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.service.FeeRuleService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 费用规则服务实现。
 */
@Service
public class FeeRuleServiceImpl implements FeeRuleService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingMapper clearingMapper;

    public FeeRuleServiceImpl(ClearingMemoryStore clearingMemoryStore, ClearingMapper clearingMapper) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingMapper = clearingMapper;
    }

    @Override
    public PageResultDTO<FeeRuleDTO> list(String feeType, String status, int pageNo, int pageSize) {
        List<FeeRuleDTO> items = clearingMemoryStore.feeRules().stream()
                .filter(item -> feeType == null || feeType.isEmpty() || feeType.equals(item.getFeeType()))
                .filter(item -> status == null || status.isEmpty() || status.equals(item.getStatus()))
                .map(clearingMapper::toFeeRuleDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public FeeRuleDTO create(CreateFeeRuleRequestDTO request) {
        return clearingMapper.toFeeRuleDTO(clearingMemoryStore.createFeeRule(
                request.getFeeName(),
                request.getFeeType(),
                request.getFeeMode(),
                request.getFeeRate(),
                request.getFixedAmount(),
                request.getFeeBearer()));
    }

    private PageResultDTO<FeeRuleDTO> page(List<FeeRuleDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
