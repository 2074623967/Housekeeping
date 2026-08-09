package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.ClearingReplayResultDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventDispatchService;
import com.abc123.clearing.service.ClearingEventService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 清分事件服务实现。
 */
@Service
public class ClearingEventServiceImpl implements ClearingEventService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingMapper clearingMapper;
    private final ClearingEventDispatchService clearingEventDispatchService;
    private final ClearingOutboxRetryService clearingOutboxRetryService;

    @Autowired
    public ClearingEventServiceImpl(ClearingMemoryStore clearingMemoryStore,
                                    ClearingMapper clearingMapper,
                                    ClearingEventDispatchService clearingEventDispatchService,
                                    ClearingOutboxRetryService clearingOutboxRetryService) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingMapper = clearingMapper;
        this.clearingEventDispatchService = clearingEventDispatchService;
        this.clearingOutboxRetryService = clearingOutboxRetryService;
    }

    ClearingEventServiceImpl(ClearingMemoryStore clearingMemoryStore, ClearingMapper clearingMapper) {
        this(clearingMemoryStore, clearingMapper, new ClearingEventDispatchService() {
            @Override
            public boolean publishClearingGenerated(String paymentOrderId) {
                return false;
            }
        }, new ClearingOutboxRetryService(clearingMemoryStore, new ClearingEventDispatchService() {
            @Override
            public boolean publishClearingGenerated(String paymentOrderId) {
                return false;
            }
        }, 100));
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
        ClearingEventDTO event = clearingMapper.toEventDTO(clearingMemoryStore.consumePaymentSuccess(request));
        clearingEventDispatchService.publishClearingGenerated(request.getPaymentOrderId());
        return event;
    }

    @Override
    public ClearingReplayResultDTO replayDueFailedEvents() {
        int attemptedCount = clearingOutboxRetryService.republishDueFailedEvents();
        ClearingReplayResultDTO dto = new ClearingReplayResultDTO();
        dto.setAttemptedCount(attemptedCount);
        dto.setMessage(attemptedCount > 0
                ? "已触发失败派发重放，请复核结算和账务下游是否完成收口"
                : "当前没有到达重试时间的失败派发事件");
        return dto;
    }

    private PageResultDTO<ClearingEventDTO> page(List<ClearingEventDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
