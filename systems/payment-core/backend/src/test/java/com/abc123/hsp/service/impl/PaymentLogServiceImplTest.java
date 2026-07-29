package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentLogQueryDTO;
import com.abc123.hsp.mapper.PaymentLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付处理日志分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentLogServiceImplTest {

    @Mock
    private PaymentLogMapper paymentLogMapper;

    @Test
    void shouldNormalizeAndForwardPagingQuery() {
        PaymentLogQueryDTO query = new PaymentLogQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setProcessStage("支付提交");
        query.setLogLevel("INFO");
        query.setSource(" wx_h5 ");
        query.setKeyword(" 回调 ");
        query.setSortField(" logLevel ");
        query.setSortOrder(" ASC ");
        query.setPageNo(3);
        query.setPageSize(25);

        new PaymentLogServiceImpl(paymentLogMapper).list(query);

        org.junit.jupiter.api.Assertions.assertEquals("ORD-001", query.getOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("wx_h5", query.getSource());
        org.junit.jupiter.api.Assertions.assertEquals("回调", query.getKeyword());
        org.junit.jupiter.api.Assertions.assertEquals("logLevel", query.getSortField());
        org.junit.jupiter.api.Assertions.assertEquals("asc", query.getSortOrder());
        verify(paymentLogMapper).findAll(query);
        verify(paymentLogMapper).count(query);
    }
}
