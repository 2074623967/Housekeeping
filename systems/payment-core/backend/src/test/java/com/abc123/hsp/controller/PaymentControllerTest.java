package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.abc123.hsp.dto.PaymentCloseRequestDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.service.PaymentService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 支付单控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private HttpServletRequest servletRequest;

    @Test
    void shouldKeepPublicQueryAndCloseOnSelfServicePath() {
        PaymentController controller = new PaymentController(paymentService);
        PaymentQueryRequestDTO queryRequest = new PaymentQueryRequestDTO();
        queryRequest.setPaymentOrderId("PAY-QUERY-PUBLIC");
        queryRequest.setOperator("forged-operator");
        PaymentCloseRequestDTO closeRequest = new PaymentCloseRequestDTO();
        closeRequest.setPaymentOrderId("PAY-CLOSE-PUBLIC");
        closeRequest.setOperator("forged-operator");

        controller.query(queryRequest);
        controller.close(closeRequest);

        verify(paymentService).query(queryRequest);
        verify(paymentService).close(closeRequest);
    }

    @Test
    void shouldForwardExplicitOperatorsFromAdminEndpoints() {
        PaymentController controller = new PaymentController(paymentService);
        PaymentQueryRequestDTO queryRequest = new PaymentQueryRequestDTO();
        queryRequest.setPaymentOrderId("PAY-QUERY-ADMIN");
        queryRequest.setOperator(" 支付运营 ");
        PaymentCloseRequestDTO closeRequest = new PaymentCloseRequestDTO();
        closeRequest.setPaymentOrderId("PAY-CLOSE-ADMIN");
        closeRequest.setOperator(" 财务运营 ");

        controller.adminQuery(queryRequest);
        controller.adminClose(closeRequest);

        verify(paymentService).query(queryRequest, " 支付运营 ");
        verify(paymentService).close(closeRequest, " 财务运营 ");
    }

    @Test
    void shouldExportPayments() {
        PaymentController controller = new PaymentController(paymentService);
        when(paymentService.exportCsv(any(PaymentListQueryDTO.class))).thenReturn("payment-csv");

        ResponseEntity<byte[]> response = controller.export(
                "PAY-001",
                "ORD-001",
                "wx_h5",
                "微信支付",
                "SUCCESS",
                1,
                20
        );

        ArgumentCaptor<PaymentListQueryDTO> captor = ArgumentCaptor.forClass(PaymentListQueryDTO.class);
        verify(paymentService).exportCsv(captor.capture());
        assertEquals("wx_h5", captor.getValue().getChannelCode());
        assertEquals("attachment; filename=payments.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals("payment-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListPayments() {
        PaymentController controller = new PaymentController(paymentService);

        controller.list(null, null, "wx_h5", "全部", "全部", 1, 20);

        ArgumentCaptor<PaymentListQueryDTO> captor = ArgumentCaptor.forClass(PaymentListQueryDTO.class);
        verify(paymentService).list(captor.capture());
        assertEquals("wx_h5", captor.getValue().getChannelCode());
    }

    @Test
    void shouldIgnoreBodyAndForwardedIpFromUntrustedPeer() {
        PaymentController controller = new PaymentController(paymentService, "10.0.0.1");
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setClientIp("192.0.2.99");
        request.setTerminal("H5");
        when(servletRequest.getRemoteAddr()).thenReturn("203.0.113.10");

        controller.submit(request, servletRequest);

        ArgumentCaptor<PaymentSubmitRequestDTO> captor = ArgumentCaptor.forClass(PaymentSubmitRequestDTO.class);
        verify(paymentService).submit(captor.capture());
        assertEquals("203.0.113.10", captor.getValue().getClientIp());
    }

    @Test
    void shouldResolveRightmostUntrustedIpFromTrustedProxyChain() {
        PaymentController controller = new PaymentController(paymentService, "10.0.0.1,10.0.0.2");
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setClientIp("192.0.2.99");
        request.setTerminal("H5");
        when(servletRequest.getRemoteAddr()).thenReturn("10.0.0.2");
        when(servletRequest.getHeader("X-Forwarded-For"))
                .thenReturn("192.0.2.5, 198.51.100.8, 10.0.0.1");

        controller.submit(request, servletRequest);

        ArgumentCaptor<PaymentSubmitRequestDTO> captor = ArgumentCaptor.forClass(PaymentSubmitRequestDTO.class);
        verify(paymentService).submit(captor.capture());
        assertEquals("198.51.100.8", captor.getValue().getClientIp());
    }

    @Test
    void shouldFallBackToTrustedPeerWhenForwardedChainIsMalformed() {
        PaymentController controller = new PaymentController(paymentService, "10.0.0.2");
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setTerminal("H5");
        when(servletRequest.getRemoteAddr()).thenReturn("10.0.0.2");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("198.51.100.8, forged-host");

        controller.submit(request, servletRequest);

        ArgumentCaptor<PaymentSubmitRequestDTO> captor = ArgumentCaptor.forClass(PaymentSubmitRequestDTO.class);
        verify(paymentService).submit(captor.capture());
        assertEquals("10.0.0.2", captor.getValue().getClientIp());
    }

    @Test
    void shouldNormalizeIpv6AddressesInTrustedProxyChain() {
        PaymentController controller = new PaymentController(paymentService, "2001:db8::2");
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setTerminal("H5");
        when(servletRequest.getRemoteAddr()).thenReturn("2001:db8::2");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("2001:db8::7");

        controller.submit(request, servletRequest);

        ArgumentCaptor<PaymentSubmitRequestDTO> captor = ArgumentCaptor.forClass(PaymentSubmitRequestDTO.class);
        verify(paymentService).submit(captor.capture());
        assertEquals("2001:db8:0:0:0:0:0:7", captor.getValue().getClientIp());
    }

    @Test
    void shouldUseUnknownWhenDirectPeerAddressIsInvalid() {
        PaymentController controller = new PaymentController(paymentService, "");
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setClientIp("192.0.2.99");
        request.setTerminal("H5");
        when(servletRequest.getRemoteAddr()).thenReturn("forged-host");

        controller.submit(request, servletRequest);

        ArgumentCaptor<PaymentSubmitRequestDTO> captor = ArgumentCaptor.forClass(PaymentSubmitRequestDTO.class);
        verify(paymentService).submit(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getClientIp());
    }

    @Test
    void shouldResolveMobileTerminalIndependentOfDefaultLocale() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            PaymentController controller = new PaymentController(paymentService, "");
            PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
            when(servletRequest.getHeader("X-Terminal")).thenReturn(null);
            when(servletRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (iPhone; Mobile)");

            controller.submit(request, servletRequest);

            ArgumentCaptor<PaymentSubmitRequestDTO> captor = ArgumentCaptor.forClass(PaymentSubmitRequestDTO.class);
            verify(paymentService).submit(captor.capture());
            assertEquals("MOBILE_WEB", captor.getValue().getTerminal());
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
