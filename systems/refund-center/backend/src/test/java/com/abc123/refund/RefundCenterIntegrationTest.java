package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.service.RefundService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 使用 H2 验证 DDL、MyBatis XML 和退款状态机的真实联动。
 */
@SpringBootTest
class RefundCenterIntegrationTest {

    @Autowired
    private RefundService refundService;

    @Test
    void shouldCompleteRefundFlowWithDatabase() {
        String paymentOrderId = "PAY-IT-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-IT-1");
        source.setCustomerName("集成测试客户");
        source.setPaidAmount(new BigDecimal("120.00"));
        source.setChannelCode("WECHAT");
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO apply = new RefundApplyRequestDTO();
        apply.setPaymentOrderId(paymentOrderId);
        apply.setRefundAmount(new BigDecimal("30.00"));
        apply.setRefundReason("服务取消");
        RefundListItemDTO created = refundService.apply(apply);

        RefundActionRequestDTO action = new RefundActionRequestDTO();
        action.setRefundOrderId(created.getRefundOrderId());
        assertEquals("APPROVED", refundService.approve(action).getStatus());
        assertEquals("PROCESSING", refundService.submit(action).getStatus());

        RefundCallbackRequestDTO callback = new RefundCallbackRequestDTO();
        callback.setRefundOrderId(created.getRefundOrderId());
        callback.setResult("SUCCESS");
        callback.setChannelRefundId("CH-IT-1");
        assertEquals("SUCCESS", refundService.callback(callback).getStatus());
        assertEquals(4, refundService.detail(created.getRefundOrderId()).getOperationLogs().size());
    }
}

