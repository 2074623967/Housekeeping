package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 本地退款渠道稳定幂等键测试。
 */
class LocalRefundChannelSubmitServiceImplTest {

    @Test
    void shouldKeepChannelRefundNoStablePerAttemptAndDistinctOnRetry() throws Exception {
        RefundChannelSubmitRequestDTO request = new RefundChannelSubmitRequestDTO();
        request.setRefundOrderId("REF-001");
        request.setRefundAttemptNo("RFA-001");
        LocalRefundChannelSubmitServiceImpl service = new LocalRefundChannelSubmitServiceImpl();

        RefundChannelSubmitResultDTO first = service.submit(request);
        RefundChannelSubmitResultDTO second = service.submit(request);
        RefundChannelSubmitRequestDTO retry = new RefundChannelSubmitRequestDTO();
        retry.setRefundOrderId("REF-001");
        retry.setRefundAttemptNo("RFA-002");
        RefundChannelSubmitResultDTO third = service.submit(retry);

        assertEquals("RCH-RFA-001", first.getChannelRefundNo());
        assertEquals(first.getChannelRefundNo(), second.getChannelRefundNo());
        assertEquals("RCH-RFA-002", third.getChannelRefundNo());
        assertNotEquals(first.getChannelRefundNo(), third.getChannelRefundNo());
        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals("RFA-001", objectMapper.readTree(first.getResponsePayload())
                .path("refundAttemptNo").asText());
        assertEquals("RFA-002", objectMapper.readTree(third.getResponsePayload())
                .path("refundAttemptNo").asText());
    }

    @Test
    void shouldRejectMissingRefundAttemptIdentity() {
        LocalRefundChannelSubmitServiceImpl service = new LocalRefundChannelSubmitServiceImpl();
        RefundChannelSubmitRequestDTO request = new RefundChannelSubmitRequestDTO();
        request.setRefundOrderId("REF-001");

        assertThrows(IllegalArgumentException.class, () -> service.submit(request));
        request.setRefundAttemptNo("  ");
        assertThrows(IllegalArgumentException.class, () -> service.submit(request));
        assertThrows(IllegalArgumentException.class, () -> service.submit(null));
    }
}
