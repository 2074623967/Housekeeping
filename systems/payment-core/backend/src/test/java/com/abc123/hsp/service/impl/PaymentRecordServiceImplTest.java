package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentRecordDetailDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.mapper.PaymentRecordMapper;
import java.util.Arrays;
import java.util.Collections;
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
    void shouldRejectInvalidPaymentOrderIdBeforeMapperAccess() {
        PaymentRecordServiceImpl service = new PaymentRecordServiceImpl(paymentRecordMapper, paymentMapper);

        BusinessException blankException = assertThrows(
                BusinessException.class, () -> service.detail("  "));
        BusinessException controlException = assertThrows(
                BusinessException.class, () -> service.detail("PAY-001\n"));
        BusinessException oversizedException = assertThrows(
                BusinessException.class,
                () -> service.detail(String.join("", Collections.nCopies(65, "P"))));

        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, blankException.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, controlException.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, oversizedException.getCode());
        verifyNoInteractions(paymentRecordMapper, paymentMapper);
    }

    @Test
    void shouldAssembleRouteNotifyAndEventLogsForPaymentRecordDetail() {
        PaymentRecordDetailDTO detail = new PaymentRecordDetailDTO();
        detail.setPaymentOrderId("PAY-DETAIL-001");
        detail.setUserId("user-张女士");
        detail.setUserPaymentChannelId("channel-user-张女士");
        detail.setLatestClientIp("10.20.30.40");
        detail.setLatestIdempotencyKey("IDEMP-RECORD-001");
        detail.setLatestRequestPayload("{\"payerPhone\":\"13800138000\"}");
        detail.setLatestResponsePayload("{\"customerName\":\"李女士\",\"code\":\"SUCCESS\"}");
        detail.setLatestAttemptStatus("WAIT_CALLBACK");
        when(paymentRecordMapper.findDetail("PAY-DETAIL-001")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-DETAIL-001"))
                .thenReturn(Arrays.asList("命中渠道优先级规则", "路由到 WX_JSAPI"));
        when(paymentMapper.findNotifyLogs("PAY-DETAIL-001"))
                .thenReturn(Arrays.asList("回调验签成功", "回调状态更新成功"));
        when(paymentMapper.findEventItems("PAY-DETAIL-001"))
                .thenReturn(Arrays.asList("PAYMENT_CREATED", "PAYMENT_WAIT_CALLBACK"));

        PaymentRecordDetailDTO result =
                new PaymentRecordServiceImpl(paymentRecordMapper, paymentMapper).detail(" PAY-DETAIL-001 ");

        assertEquals("PAY-DETAIL-001", result.getPaymentOrderId());
        assertEquals("user-****", result.getUserId());
        assertEquals("channel-****", result.getUserPaymentChannelId());
        assertEquals("10.20.*.*", result.getLatestClientIp());
        assertEquals("IDEMP-****-001", result.getLatestIdempotencyKey());
        assertEquals("{\"payerPhone\":\"138****8000\"}", result.getLatestRequestPayload());
        assertEquals("{\"customerName\":\"李*\",\"code\":\"SUCCESS\"}", result.getLatestResponsePayload());
        assertEquals(2, result.getRouteLogs().size());
        assertEquals(2, result.getNotifyLogs().size());
        assertEquals(2, result.getEventLogs().size());
        assertEquals("user-张女士", detail.getUserId());
        assertEquals("10.20.30.40", detail.getLatestClientIp());
        assertEquals("IDEMP-RECORD-001", detail.getLatestIdempotencyKey());
        assertEquals("{\"payerPhone\":\"13800138000\"}", detail.getLatestRequestPayload());
        assertEquals(null, detail.getRouteLogs());
        verify(paymentMapper, times(1)).findRouteLogs("PAY-DETAIL-001");
        verify(paymentMapper, times(1)).findNotifyLogs("PAY-DETAIL-001");
        verify(paymentMapper, times(1)).findEventItems("PAY-DETAIL-001");
    }
}
