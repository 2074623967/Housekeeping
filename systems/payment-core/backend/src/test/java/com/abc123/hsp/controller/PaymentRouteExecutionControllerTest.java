package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentRouteExecutionOverviewDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import com.abc123.hsp.service.PaymentRouteExecutionService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 支付路由执行结果控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRouteExecutionControllerTest {

    @Mock
    private PaymentRouteExecutionService paymentRouteExecutionService;

    @Test
    void shouldListPaymentRouteExecutions() {
        PaymentRouteExecutionController controller = new PaymentRouteExecutionController(paymentRouteExecutionService);

        controller.list(null, null, null, null, "全部", "全部", "全部", "createdAt", "desc", 1, 20);

        verify(paymentRouteExecutionService).list(any(PaymentRouteExecutionQueryDTO.class));
    }

    @Test
    void shouldReturnPaymentRouteExecutionOverview() {
        PaymentRouteExecutionController controller = new PaymentRouteExecutionController(paymentRouteExecutionService);
        PaymentRouteExecutionOverviewDTO overviewDTO = new PaymentRouteExecutionOverviewDTO();
        overviewDTO.setTotalRouteCount(6L);
        when(paymentRouteExecutionService.overview(any(PaymentRouteExecutionQueryDTO.class))).thenReturn(overviewDTO);

        controller.overview("PAY-001", "ORD-001", "RULE_HOME_WX", "wx_h5", "微信", "H5", "命中规则路由", "createdAt", "desc");

        verify(paymentRouteExecutionService).overview(any(PaymentRouteExecutionQueryDTO.class));
    }

    @Test
    void shouldExportPaymentRouteExecutions() {
        PaymentRouteExecutionController controller = new PaymentRouteExecutionController(paymentRouteExecutionService);
        when(paymentRouteExecutionService.exportCsv(any(PaymentRouteExecutionQueryDTO.class))).thenReturn("route-csv");

        ResponseEntity<byte[]> response = controller.export(
                "PAY-001", "ORD-001", "RULE_HOME_WX", "wx_h5", "微信", "H5",
                "命中规则路由", "channelCode", "asc");

        org.mockito.ArgumentCaptor<PaymentRouteExecutionQueryDTO> queryCaptor =
                org.mockito.ArgumentCaptor.forClass(PaymentRouteExecutionQueryDTO.class);
        verify(paymentRouteExecutionService).exportCsv(queryCaptor.capture());
        PaymentRouteExecutionQueryDTO query = queryCaptor.getValue();
        assertNotNull(query);
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("RULE_HOME_WX", query.getRouteRule());
        assertEquals("wx_h5", query.getChannelCode());
        assertEquals("微信", query.getPaymentMethod());
        assertEquals("H5", query.getTerminal());
        assertEquals("命中规则路由", query.getRouteResult());
        assertEquals("channelCode", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        assertEquals("attachment; filename=payment-routes.csv",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(new MediaType("text", "csv", StandardCharsets.UTF_8),
                response.getHeaders().getContentType());
        assertArrayEquals("route-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }
}
