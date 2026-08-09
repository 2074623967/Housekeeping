package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestOverviewDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.service.PaymentRequestService;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 支付请求管理控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRequestControllerTest {

    @Mock
    private PaymentRequestService paymentRequestService;

    @Test
    void shouldListPaymentRequests() {
        PaymentRequestController controller = new PaymentRequestController(paymentRequestService);
        PaymentRequestListItemDTO itemDTO = new PaymentRequestListItemDTO();
        itemDTO.setRequestNo("REQ-001");
        PageResultDTO<PaymentRequestListItemDTO> resultDTO =
                new PageResultDTO<PaymentRequestListItemDTO>(Collections.singletonList(itemDTO), 1L, 2, 50);
        when(paymentRequestService.list(any(PaymentRequestQueryDTO.class))).thenReturn(resultDTO);

        controller.list("REQ-001", "PAY-001", "ORD-001", "wx_h5", "H5", "127.0.0.1",
                "请求成功", "createdAt", "desc", 2, 50);

        ArgumentCaptor<PaymentRequestQueryDTO> queryCaptor = ArgumentCaptor.forClass(PaymentRequestQueryDTO.class);
        verify(paymentRequestService).list(queryCaptor.capture());
        PaymentRequestQueryDTO queryDTO = queryCaptor.getValue();

        assertNotNull(queryDTO);
        assertEquals("REQ-001", queryDTO.getRequestNo());
        assertEquals("PAY-001", queryDTO.getPaymentOrderId());
        assertEquals("ORD-001", queryDTO.getOrderNo());
        assertEquals("wx_h5", queryDTO.getChannelCode());
        assertEquals("H5", queryDTO.getTerminal());
        assertEquals("127.0.0.1", queryDTO.getClientIp());
        assertEquals("请求成功", queryDTO.getRequestStatus());
        assertEquals("createdAt", queryDTO.getSortField());
        assertEquals("desc", queryDTO.getSortOrder());
        assertEquals(2, queryDTO.getPageNo());
        assertEquals(50, queryDTO.getPageSize());
    }

    @Test
    void shouldReturnPaymentRequestOverview() {
        PaymentRequestController controller = new PaymentRequestController(paymentRequestService);
        PaymentRequestOverviewDTO overviewDTO = new PaymentRequestOverviewDTO();
        overviewDTO.setTotalRequestCount(18L);
        when(paymentRequestService.overview(any(PaymentRequestQueryDTO.class))).thenReturn(overviewDTO);

        controller.overview("REQ-001", "PAY-001", "ORD-001", "wx_h5", "H5", "127.0.0.1",
                "请求成功", "createdAt", "desc");

        ArgumentCaptor<PaymentRequestQueryDTO> queryCaptor = ArgumentCaptor.forClass(PaymentRequestQueryDTO.class);
        verify(paymentRequestService).overview(queryCaptor.capture());
        PaymentRequestQueryDTO queryDTO = queryCaptor.getValue();

        assertEquals("REQ-001", queryDTO.getRequestNo());
        assertEquals("PAY-001", queryDTO.getPaymentOrderId());
        assertEquals("ORD-001", queryDTO.getOrderNo());
        assertEquals("wx_h5", queryDTO.getChannelCode());
        assertEquals("H5", queryDTO.getTerminal());
        assertEquals("127.0.0.1", queryDTO.getClientIp());
        assertEquals("请求成功", queryDTO.getRequestStatus());
    }

    @Test
    void shouldExportPaymentRequests() {
        PaymentRequestController controller = new PaymentRequestController(paymentRequestService);
        when(paymentRequestService.exportCsv(any(PaymentRequestQueryDTO.class))).thenReturn("request-csv");

        ResponseEntity<byte[]> response = controller.export("REQ-002", "PAY-002", "ORD-002",
                "alipay_h5", "APP", "10.0.0.1", "请求失败", "channelCode", "asc");

        ArgumentCaptor<PaymentRequestQueryDTO> queryCaptor = ArgumentCaptor.forClass(PaymentRequestQueryDTO.class);
        verify(paymentRequestService).exportCsv(queryCaptor.capture());
        PaymentRequestQueryDTO queryDTO = queryCaptor.getValue();

        assertNotNull(queryDTO);
        assertEquals("REQ-002", queryDTO.getRequestNo());
        assertEquals("PAY-002", queryDTO.getPaymentOrderId());
        assertEquals("ORD-002", queryDTO.getOrderNo());
        assertEquals("alipay_h5", queryDTO.getChannelCode());
        assertEquals("APP", queryDTO.getTerminal());
        assertEquals("10.0.0.1", queryDTO.getClientIp());
        assertEquals("请求失败", queryDTO.getRequestStatus());
        assertEquals("channelCode", queryDTO.getSortField());
        assertEquals("asc", queryDTO.getSortOrder());
        assertEquals("attachment; filename=payment-requests.csv",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(new MediaType("text", "csv", StandardCharsets.UTF_8), response.getHeaders().getContentType());
        assertArrayEquals("request-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }
}
