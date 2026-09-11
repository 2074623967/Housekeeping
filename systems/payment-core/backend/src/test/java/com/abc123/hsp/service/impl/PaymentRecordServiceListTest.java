package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.mapper.PaymentRecordMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付记录列表查询服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecordServiceListTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Test
    void shouldNormalizePaymentRecordListQuery() {
        PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
        query.setRecordType(" ALL ");
        query.setUserId(" 张女士 ");
        query.setBusinessOrderNo(" ORD-001 ");
        query.setPaymentType(" 消费支付 ");
        query.setPaymentStatus(" 支付成功 ");
        query.setPaymentChannel(" wx_jsapi ");
        query.setSortField(" paymentAmount ");
        query.setSortOrder(" ASC ");
        query.setPageNo(0);
        query.setPageSize(999);

        org.mockito.Mockito.when(paymentRecordMapper.findAll(query)).thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(paymentRecordMapper.count(query)).thenReturn(0L);
        new PaymentRecordServiceImpl(paymentRecordMapper, paymentMapper).list(query);

        assertEquals("ALL", query.getRecordType());
        assertEquals("张女士", query.getUserId());
        assertEquals("ORD-001", query.getBusinessOrderNo());
        assertEquals("消费支付", query.getPaymentType());
        assertEquals("支付成功", query.getPaymentStatus());
        assertEquals("wx_jsapi", query.getPaymentChannel());
        assertEquals("paymentAmount", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        assertEquals(1, query.getPageNo());
        assertEquals(100, query.getPageSize());
        verify(paymentRecordMapper).findAll(query);
        verify(paymentRecordMapper).count(query);
    }

    @Test
    void shouldRejectOversizedPaymentRecordListQueryBeforeMapperAccess() {
        PaymentRecordQueryDTO recordTypeQuery = new PaymentRecordQueryDTO();
        recordTypeQuery.setRecordType(repeat("R", 17));
        PaymentRecordQueryDTO userQuery = new PaymentRecordQueryDTO();
        userQuery.setUserId(repeat("张", 129));
        PaymentRecordQueryDTO orderQuery = new PaymentRecordQueryDTO();
        orderQuery.setBusinessOrderNo(repeat("O", 65));
        PaymentRecordQueryDTO paymentTypeQuery = new PaymentRecordQueryDTO();
        paymentTypeQuery.setPaymentType(repeat("T", 33));
        PaymentRecordQueryDTO statusQuery = new PaymentRecordQueryDTO();
        statusQuery.setPaymentStatus(repeat("S", 33));
        PaymentRecordQueryDTO channelQuery = new PaymentRecordQueryDTO();
        channelQuery.setPaymentChannel(repeat("C", 65));
        PaymentRecordServiceImpl service = new PaymentRecordServiceImpl(paymentRecordMapper, paymentMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(recordTypeQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(userQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(paymentTypeQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(statusQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(channelQuery));
        verifyNoInteractions(paymentRecordMapper, paymentMapper);
    }

    @Test
    void shouldRejectControlCharactersInPaymentRecordListQueryBeforeMapperAccess() {
        PaymentRecordQueryDTO recordTypeQuery = new PaymentRecordQueryDTO();
        recordTypeQuery.setRecordType("ALL\n");
        PaymentRecordQueryDTO userQuery = new PaymentRecordQueryDTO();
        userQuery.setUserId("张女士\n");
        PaymentRecordQueryDTO orderQuery = new PaymentRecordQueryDTO();
        orderQuery.setBusinessOrderNo("ORD-001\n");
        PaymentRecordQueryDTO paymentTypeQuery = new PaymentRecordQueryDTO();
        paymentTypeQuery.setPaymentType("消费支付\n");
        PaymentRecordQueryDTO statusQuery = new PaymentRecordQueryDTO();
        statusQuery.setPaymentStatus("支付成功\n");
        PaymentRecordQueryDTO channelQuery = new PaymentRecordQueryDTO();
        channelQuery.setPaymentChannel("wx_h5\n");
        PaymentRecordServiceImpl service = new PaymentRecordServiceImpl(paymentRecordMapper, paymentMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(recordTypeQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(userQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(paymentTypeQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(statusQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(channelQuery));
        verifyNoInteractions(paymentRecordMapper, paymentMapper);
    }

    @Test
    void shouldMaskUserReferencesWithoutChangingMapperDto() {
        PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
        PaymentRecordRowDTO row = new PaymentRecordRowDTO();
        row.setPaymentOrderId("PAY-001");
        row.setUserId("user-张女士");
        row.setUserPaymentChannelId("channel-user-张女士");
        org.mockito.Mockito.when(paymentRecordMapper.findAll(query))
                .thenReturn(Collections.singletonList(row));
        org.mockito.Mockito.when(paymentRecordMapper.count(query)).thenReturn(1L);

        PageResultDTO<PaymentRecordRowDTO> result =
                new PaymentRecordServiceImpl(paymentRecordMapper, paymentMapper).list(query);

        assertEquals("user-****", result.getItems().get(0).getUserId());
        assertEquals("channel-****", result.getItems().get(0).getUserPaymentChannelId());
        assertEquals("user-张女士", row.getUserId());
        assertEquals("channel-user-张女士", row.getUserPaymentChannelId());
    }

    private String repeat(String value, int count) {
        return String.join("", Collections.nCopies(count, value));
    }
}
