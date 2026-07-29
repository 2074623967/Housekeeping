package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.ClearingRuleDTO;
import com.abc123.clearing.dto.CreateClearingRuleRequestDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.service.ClearingRuleService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 清分规则服务实现。
 */
@Service
public class ClearingRuleServiceImpl implements ClearingRuleService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingMapper clearingMapper;

    public ClearingRuleServiceImpl(ClearingMemoryStore clearingMemoryStore, ClearingMapper clearingMapper) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingMapper = clearingMapper;
    }

    @Override
    public PageResultDTO<ClearingRuleDTO> list(String ruleType, String ruleStatus, int pageNo, int pageSize) {
        List<ClearingRuleDTO> items = clearingMemoryStore.rules().stream()
                .filter(item -> ruleType == null || ruleType.isEmpty() || ruleType.equals(item.getRuleType()))
                .filter(item -> ruleStatus == null || ruleStatus.isEmpty() || ruleStatus.equals(item.getRuleStatus()))
                .map(clearingMapper::toRuleDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public ClearingRuleDTO create(CreateClearingRuleRequestDTO request) {
        return clearingMapper.toRuleDTO(clearingMemoryStore.createRule(
                request.getRuleName(),
                request.getRuleType(),
                request.getRuleExpression(),
                request.getGreyFlag()));
    }

    @Override
    public ClearingRuleDTO enable(String ruleNo) {
        return clearingMapper.toRuleDTO(clearingMemoryStore.updateRuleStatus(ruleNo, "启用"));
    }

    @Override
    public ClearingRuleDTO disable(String ruleNo) {
        return clearingMapper.toRuleDTO(clearingMemoryStore.updateRuleStatus(ruleNo, "停用"));
    }

    private PageResultDTO<ClearingRuleDTO> page(List<ClearingRuleDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
