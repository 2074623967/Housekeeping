package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentRecordQueryDTO;
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
}
