package com.abc123.gatewayaccess.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.abc123.gatewayaccess.dto.PaymentOrderQueryRequestDTO;
import com.abc123.gatewayaccess.service.PaymentOrderQueryService;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class GatewayTransactionControllerTest {

    @Test
    void shouldDelegatePaymentQueryWithCallerContext() {
        PaymentOrderQueryService service = mock(PaymentOrderQueryService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        PaymentOrderQueryRequestDTO request = new PaymentOrderQueryRequestDTO();

        new GatewayTransactionController(service)
                .queryPaymentOrder("APP_PAY_CORE", "secret", request, servletRequest);

        verify(service).query("APP_PAY_CORE", "secret", "127.0.0.1", request);
    }
}
