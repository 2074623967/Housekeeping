package com.abc123.clearing.service.impl;

import com.abc123.clearing.entity.ClearingEventEntity;
import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.service.ClearingEventDispatchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Replays only due failed clearing outbox records through the existing publisher. */
@Service
public class ClearingOutboxRetryService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingEventDispatchService clearingEventDispatchService;
    private final int batchSize;

    public ClearingOutboxRetryService(ClearingMemoryStore clearingMemoryStore,
                                      ClearingEventDispatchService clearingEventDispatchService,
                                      @Value("${clearing.outbox-retry.batch-size:100}") int batchSize) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingEventDispatchService = clearingEventDispatchService;
        this.batchSize = Math.max(batchSize, 1);
    }

    public int republishDueFailedEvents() {
        int attempted = 0;
        for (ClearingEventEntity event : clearingMemoryStore.dueFailedClearingGeneratedEvents(batchSize)) {
            ClearingOrderEntity order = clearingMemoryStore.findOrder(event.getBizNo());
            if (order == null || order.getPaymentOrderId() == null || order.getPaymentOrderId().isEmpty()) {
                continue;
            }
            clearingEventDispatchService.publishClearingGenerated(order.getPaymentOrderId());
            attempted++;
        }
        return attempted;
    }
}
