package com.abc123.settlement.controller;

import static org.mockito.Mockito.verify;

import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import com.abc123.settlement.dto.CreateSettlementOrderRequestDTO;
import com.abc123.settlement.service.SettlementOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 结算单控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class SettlementOrderControllerTest {

    @Mock
    private SettlementOrderService settlementOrderService;

    @Test
    void shouldListSettlementOrders() {
        SettlementOrderController controller = new SettlementOrderController(settlementOrderService);

        controller.list("SET10001", "WORKER", "待审核", "CLO20001", 1, 20);

        verify(settlementOrderService).list("SET10001", "WORKER", "待审核", "CLO20001", 1, 20);
    }

    @Test
    void shouldCreateSettlementOrder() {
        SettlementOrderController controller = new SettlementOrderController(settlementOrderService);
        CreateSettlementOrderRequestDTO request = new CreateSettlementOrderRequestDTO();

        controller.create(request);

        verify(settlementOrderService).create(request);
    }

    @Test
    void shouldReturnSettlementOrderDetail() {
        SettlementOrderController controller = new SettlementOrderController(settlementOrderService);

        controller.detail("SLT20001");

        verify(settlementOrderService).detail("SLT20001");
    }

    @Test
    void shouldReturnSettlementOrderFullDetail() {
        SettlementOrderController controller = new SettlementOrderController(settlementOrderService);

        controller.fullDetail("SLT20001");

        verify(settlementOrderService).fullDetail("SLT20001");
    }

    @Test
    void shouldAuditSettlementOrder() {
        SettlementOrderController controller = new SettlementOrderController(settlementOrderService);
        AuditSettlementRequestDTO request = new AuditSettlementRequestDTO();

        controller.audit("SLT20001", request);

        verify(settlementOrderService).audit("SLT20001", request);
    }

    @Test
    void shouldRejectSettlementOrder() {
        SettlementOrderController controller = new SettlementOrderController(settlementOrderService);
        AuditSettlementRequestDTO request = new AuditSettlementRequestDTO();

        controller.reject("SLT20001", request);

        verify(settlementOrderService).reject("SLT20001", request);
    }
}
