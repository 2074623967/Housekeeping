package com.abc123.refund.dao.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOperationLogDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.mapper.RefundMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundDaoImplTest {

    @Mock
    private RefundMapper refundMapper;

    @Test
    void shouldNormalizeRefundListAndDetailCollections() {
        RefundListItemDTO refund = new RefundListItemDTO();
        RefundOperationLogDTO log = new RefundOperationLogDTO();
        RefundOutboxItemDTO outbox = new RefundOutboxItemDTO();
        when(refundMapper.findList(any(RefundQueryDTO.class)))
                .thenReturn(Arrays.asList(null, refund));
        when(refundMapper.findLogs("REF-001"))
                .thenReturn(Arrays.asList(null, log));
        when(refundMapper.findOutboxList(any(RefundOutboxQueryDTO.class)))
                .thenReturn(Arrays.asList(null, outbox));

        RefundDaoImpl dao = new RefundDaoImpl(refundMapper);

        assertEquals(Collections.singletonList(refund), dao.findList(new RefundQueryDTO()));
        assertEquals(Collections.singletonList(log), dao.findLogs("REF-001"));
        assertEquals(Collections.singletonList(outbox), dao.findOutboxList(new RefundOutboxQueryDTO()));
    }

    @Test
    void shouldNormalizeNullRefundLists() {
        when(refundMapper.findList(any(RefundQueryDTO.class))).thenReturn(null);
        when(refundMapper.findLogs("REF-EMPTY")).thenReturn(null);
        when(refundMapper.findOutboxList(any(RefundOutboxQueryDTO.class))).thenReturn(null);

        RefundDaoImpl dao = new RefundDaoImpl(refundMapper);

        assertEquals(Collections.emptyList(), dao.findList(new RefundQueryDTO()));
        assertEquals(Collections.emptyList(), dao.findLogs("REF-EMPTY"));
        assertEquals(Collections.emptyList(), dao.findOutboxList(new RefundOutboxQueryDTO()));
    }
}
