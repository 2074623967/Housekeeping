package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 清分事件服务实现。
 */
@Service
public class ClearingEventServiceImpl implements ClearingEventService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingMapper clearingMapper;

    public ClearingEventServiceImpl(ClearingMemoryStore clearingMemoryStore, ClearingMapper clearingMapper) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingMapper = clearingMapper;
    }

    @Override
    public PageResultDTO<ClearingEventDTO> list(String eventType, String bizNo, int pageNo, int pageSize) {
        List<ClearingEventDTO> items = clearingMemoryStore.events().stream()
                .filter(item -> eventType == null || eventType.isEmpty() || eventType.equals(item.getEventType()))
                .filter(item -> bizNo == null || bizNo.isEmpty() || bizNo.equals(item.getBizNo()))
                .map(clearingMapper::toEventDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public ClearingEventDTO consumePaymentSuccess(PaymentSuccessEventRequestDTO request) {
        return clearingMapper.toEventDTO(clearingMemoryStore.consumePaymentSuccess(request));
    }

    private PageResultDTO<ClearingEventDTO> page(List<ClearingEventDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
