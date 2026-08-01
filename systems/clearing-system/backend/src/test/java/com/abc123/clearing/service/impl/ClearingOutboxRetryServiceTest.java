package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.clearing.entity.ClearingEventEntity;
import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.service.ClearingEventDispatchService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Due failed outbox replay selection tests. */
@ExtendWith(MockitoExtension.class)
class ClearingOutboxRetryServiceTest {

    @Mock
    private ClearingMemoryStore clearingMemoryStore;
    @Mock
    private ClearingEventDispatchService clearingEventDispatchService;

    @Test
    void shouldRepublishOnlyDueEventsWithResolvablePaymentOrders() {
        ClearingEventEntity publishable = event("CLR-001");
        ClearingEventEntity orphan = event("CLR-002");
        when(clearingMemoryStore.dueFailedClearingGeneratedEvents(100)).thenReturn(Arrays.asList(publishable, orphan));
        ClearingOrderEntity order = new ClearingOrderEntity();
        order.setPaymentOrderId("PAY-001");
        when(clearingMemoryStore.findOrder("CLR-001")).thenReturn(order);

        int attempted = service().republishDueFailedEvents();

        assertEquals(1, attempted);
        verify(clearingEventDispatchService).publishClearingGenerated("PAY-001");
    }

    @Test
    void shouldDoNothingWhenNoDueFailedEventsExist() {
        when(clearingMemoryStore.dueFailedClearingGeneratedEvents(100)).thenReturn(Collections.emptyList());

        assertEquals(0, service().republishDueFailedEvents());
    }

    private ClearingOutboxRetryService service() {
        return new ClearingOutboxRetryService(clearingMemoryStore, clearingEventDispatchService, 100);
    }

    private ClearingEventEntity event(String clearingNo) {
        ClearingEventEntity event = new ClearingEventEntity();
        event.setBizNo(clearingNo);
        return event;
    }
}
