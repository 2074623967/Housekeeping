package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryTaskResultDTO;
import com.abc123.hsp.dto.RefundStatusQueryRequestDTO;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.RefundService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundQueryTaskServiceImplTest {

    @Mock
    private PaymentTaskCenterMapper paymentTaskCenterMapper;
    @Mock
    private RefundService refundService;

    @Test
    void shouldQueryManualCandidatesAndIsolateSingleRefundFailure() {
        when(paymentTaskCenterMapper.findPendingRefundOrderIdsForManualQuery(30, 20, 100))
                .thenReturn(Arrays.asList("REF-SUCCESS", "REF-PROCESSING", "REF-FAILED"));
        when(refundService.query(any())).thenAnswer(invocation -> {
            String refundOrderId = ((com.abc123.hsp.dto.RefundStatusQueryRequestDTO)
                    invocation.getArgument(0)).getRefundOrderId();
            if ("REF-FAILED".equals(refundOrderId)) {
                throw new IllegalStateException("gateway unavailable");
            }
            RefundListItemDTO refund = new RefundListItemDTO();
            refund.setStatus("REF-SUCCESS".equals(refundOrderId) ? "SUCCESS" : "PROCESSING");
            return refund;
        });

        RefundQueryTaskResultDTO result = service().queryPendingRefunds("payment-core-admin");

        assertEquals(3, result.getProcessedCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getWarningCount());
        assertEquals(1, result.getFailCount());
        assertEquals(Collections.singletonList("REF-FAILED"), result.getFailedRefundOrderIds());
        ArgumentCaptor<RefundStatusQueryRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(RefundStatusQueryRequestDTO.class);
        verify(refundService, org.mockito.Mockito.times(3)).query(requestCaptor.capture());
        requestCaptor.getAllValues().forEach(request ->
                assertEquals("payment-core-admin", request.getOperator()));
    }

    @Test
    void shouldUseBackoffCandidatesAndExposeCounts() {
        when(paymentTaskCenterMapper.findDuePendingRefundOrderIdsForQuery(30, 60, 2, 900, 20, 100))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.countPendingRefundQueryBacklog(30)).thenReturn(8);
        when(paymentTaskCenterMapper.countDuePendingRefundQueries(30, 60, 2, 900)).thenReturn(3);

        RefundQueryTaskServiceImpl service = service();

        assertEquals(0, service.queryDueRefunds("refund-query-scheduler").getProcessedCount());
        assertEquals(8, service.countPendingRefunds());
        assertEquals(3, service.countDueRefunds());
        verify(paymentTaskCenterMapper).findDuePendingRefundOrderIdsForQuery(30, 60, 2, 900, 20, 100);
    }

    @Test
    void shouldClampGlobalAndPerChannelQueryBatchSizes() {
        when(paymentTaskCenterMapper.findPendingRefundOrderIdsForManualQuery(30, 1, 1))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findDuePendingRefundOrderIdsForQuery(30, 60, 2, 900, 1000, 1000))
                .thenReturn(Collections.emptyList());

        new RefundQueryTaskServiceImpl(
                paymentTaskCenterMapper, refundService, 30, 60, 2, 900, 0, 0)
                .queryPendingRefunds("payment-core-admin");
        new RefundQueryTaskServiceImpl(
                paymentTaskCenterMapper, refundService, 30, 60, 2, 900, 1001, 1001)
                .queryDueRefunds("refund-query-scheduler");

        verify(paymentTaskCenterMapper).findPendingRefundOrderIdsForManualQuery(30, 1, 1);
        verify(paymentTaskCenterMapper).findDuePendingRefundOrderIdsForQuery(
                30, 60, 2, 900, 1000, 1000);
    }

    private RefundQueryTaskServiceImpl service() {
        return new RefundQueryTaskServiceImpl(
                paymentTaskCenterMapper, refundService, 30, 60, 2, 900, 20, 100);
    }
}
