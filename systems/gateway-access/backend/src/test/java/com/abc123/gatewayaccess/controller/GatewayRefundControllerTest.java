package com.abc123.gatewayaccess.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.gatewayaccess.dto.RefundOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.service.RefundOrderQueryService;
import com.abc123.gatewayaccess.service.RefundOrderSubmitService;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class GatewayRefundControllerTest {

    @Test
    void shouldDelegateRefundQueryWithCallerContext() {
        RefundOrderQueryService service = mock(RefundOrderQueryService.class);
        RefundOrderSubmitService submitService = mock(RefundOrderSubmitService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        RefundOrderQueryRequestDTO request = new RefundOrderQueryRequestDTO();

        new GatewayRefundController(service, submitService)
                .queryRefundOrder("APP_PAY_CORE", "secret", request, servletRequest);

        verify(service).query("APP_PAY_CORE", "secret", "127.0.0.1", request);
    }

    @Test
    void shouldDelegateRefundSubmitWithCallerContext() {
        RefundOrderQueryService queryService = mock(RefundOrderQueryService.class);
        RefundOrderSubmitService submitService = mock(RefundOrderSubmitService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        RefundOrderSubmitRequestDTO request = new RefundOrderSubmitRequestDTO();

        new GatewayRefundController(queryService, submitService)
                .submitRefundOrder("APP_PAY_CORE", "secret", request, servletRequest);

        verify(submitService).submit("APP_PAY_CORE", "secret", "127.0.0.1", request);
    }
}
