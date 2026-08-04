package com.abc123.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
import com.abc123.reconciliation.dto.InternalRecordRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchCreateRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.service.ReconciliationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 DDL、Mapper 和自动匹配规则。
 */
@SpringBootTest
class ReconciliationIntegrationTest {

    @Autowired
    private ReconciliationService service;

    @Test
    void shouldMatchAndDetectAmountDifference() {
        ReconciliationBatchCreateRequestDTO request = new ReconciliationBatchCreateRequestDTO();
        request.setBusinessDate("2026-08-04");
        request.setChannelCode("WECHAT");
        ReconciliationBatchListItemDTO batch = service.createBatch(request);

        ChannelRecordRequestDTO channel = new ChannelRecordRequestDTO();
        channel.setChannelTradeNo("WX-IT-1");
        channel.setPaymentOrderId("PAY-IT-1");
        channel.setAmount(new BigDecimal("100.00"));
        service.addChannelRecord(batch.getBatchNo(), channel);

        InternalRecordRequestDTO internal = new InternalRecordRequestDTO();
        internal.setPaymentOrderId("PAY-IT-1");
        internal.setAmount(new BigDecimal("99.00"));
        service.addInternalRecord(batch.getBatchNo(), internal);

        ReconciliationBatchListItemDTO result = service.run(batch.getBatchNo());
        assertEquals(1, result.getDifferenceCount());
        assertEquals(0, result.getMatchedCount());
    }
}

