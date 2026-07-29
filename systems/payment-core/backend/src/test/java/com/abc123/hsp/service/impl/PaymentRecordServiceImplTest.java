package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentRecordDetailDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.mapper.PaymentRecordMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付记录详情服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecordServiceImplTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Test
    void shouldThrowBusinessExceptionWhenPaymentRecordMissing() {
        when(paymentRecordMapper.findDetail("PAY-MISSING")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentRecordServiceImpl(paymentRecordMapper, paymentMapper).detail("PAY-MISSING")
        );

        assertEquals(ErrorCode.PAYMENT_ORDER_NOT_FOUND, exception.getCode());
        assertEquals("支付单不存在", exception.getMessage());
    }

    @Test
    void shouldAssembleRouteNotifyAndEventLogsForPaymentRecordDetail() {
        PaymentRecordDetailDTO detail = new PaymentRecordDetailDTO();
        detail.setPaymentOrderId("PAY-DETAIL-001");
        detail.setLatestAttemptStatus("WAIT_CALLBACK");
        when(paymentRecordMapper.findDetail("PAY-DETAIL-001")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-DETAIL-001"))
                .thenReturn(Arrays.asList("命中渠道优先级规则", "路由到 WX_JSAPI"));
        when(paymentMapper.findNotifyLogs("PAY-DETAIL-001"))
                .thenReturn(Arrays.asList("回调验签成功", "回调状态更新成功"));
        when(paymentMapper.findEventItems("PAY-DETAIL-001"))
                .thenReturn(Arrays.asList("PAYMENT_CREATED", "PAYMENT_WAIT_CALLBACK"));

        PaymentRecordDetailDTO result =
                new PaymentRecordServiceImpl(paymentRecordMapper, paymentMapper).detail("PAY-DETAIL-001");

        assertEquals("PAY-DETAIL-001", result.getPaymentOrderId());
        assertEquals(2, result.getRouteLogs().size());
        assertEquals(2, result.getNotifyLogs().size());
        assertEquals(2, result.getEventLogs().size());
        verify(paymentMapper, times(1)).findRouteLogs("PAY-DETAIL-001");
        verify(paymentMapper, times(1)).findNotifyLogs("PAY-DETAIL-001");
        verify(paymentMapper, times(1)).findEventItems("PAY-DETAIL-001");
    }
}
