package com.abc123.gatewayaccess.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.abc123.gatewayaccess.dto.PaymentOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.service.PaymentOrderQueryService;
import com.abc123.gatewayaccess.service.PaymentOrderSubmitService;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class GatewayTransactionControllerTest {

    @Test
    void shouldDelegatePaymentQueryWithCallerContext() {
        PaymentOrderQueryService service = mock(PaymentOrderQueryService.class);
        PaymentOrderSubmitService submitService = mock(PaymentOrderSubmitService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        PaymentOrderQueryRequestDTO request = new PaymentOrderQueryRequestDTO();

        new GatewayTransactionController(service, submitService)
                .queryPaymentOrder("APP_PAY_CORE", "secret", request, servletRequest);

        verify(service).query("APP_PAY_CORE", "secret", "127.0.0.1", request);
    }

    @Test
    void shouldDelegatePaymentSubmitWithCallerContext() {
        PaymentOrderQueryService queryService = mock(PaymentOrderQueryService.class);
        PaymentOrderSubmitService submitService = mock(PaymentOrderSubmitService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        PaymentOrderSubmitRequestDTO request = new PaymentOrderSubmitRequestDTO();

        new GatewayTransactionController(queryService, submitService)
                .submitPaymentOrder("APP_PAY_CORE", "secret", request, servletRequest);

        verify(submitService).submit("APP_PAY_CORE", "secret", "127.0.0.1", request);
    }
}
