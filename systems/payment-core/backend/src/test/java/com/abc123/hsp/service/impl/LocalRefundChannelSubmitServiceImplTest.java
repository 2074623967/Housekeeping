package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import org.junit.jupiter.api.Test;

/**
 * 本地退款渠道稳定幂等键测试。
 */
class LocalRefundChannelSubmitServiceImplTest {

    @Test
    void shouldReturnSameChannelRefundNoForSameRefundOrder() {
        RefundChannelSubmitRequestDTO request = new RefundChannelSubmitRequestDTO();
        request.setRefundOrderId("REF-001");
        LocalRefundChannelSubmitServiceImpl service = new LocalRefundChannelSubmitServiceImpl();

        RefundChannelSubmitResultDTO first = service.submit(request);
        RefundChannelSubmitResultDTO second = service.submit(request);

        assertEquals("RCH-REF-001", first.getChannelRefundNo());
        assertEquals(first.getChannelRefundNo(), second.getChannelRefundNo());
    }
}
