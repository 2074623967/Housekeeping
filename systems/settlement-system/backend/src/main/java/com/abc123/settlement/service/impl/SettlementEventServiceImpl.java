package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.service.SettlementEventService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 结算事件服务实现。
 */
@Service
public class SettlementEventServiceImpl implements SettlementEventService {

    private final SettlementMemoryStore settlementMemoryStore;
    private final SettlementMapper settlementMapper;

    public SettlementEventServiceImpl(SettlementMemoryStore settlementMemoryStore, SettlementMapper settlementMapper) {
        this.settlementMemoryStore = settlementMemoryStore;
        this.settlementMapper = settlementMapper;
    }

    @Override
    public PageResultDTO<SettlementEventDTO> list(String eventType, String bizNo, int pageNo, int pageSize) {
        List<SettlementEventDTO> items = settlementMemoryStore.events().stream()
                .filter(item -> eventType == null || eventType.isEmpty() || eventType.equals(item.getEventType()))
                .filter(item -> bizNo == null || bizNo.isEmpty() || bizNo.equals(item.getBizNo()))
                .map(settlementMapper::toEventDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public SettlementEventDTO consumeClearingGenerated(ClearingGeneratedEventRequestDTO request) {
        return settlementMapper.toEventDTO(settlementMemoryStore.consumeClearingGenerated(request));
    }

    private PageResultDTO<SettlementEventDTO> page(List<SettlementEventDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
