package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;

import com.abc123.accounting.dto.BalanceOperationRequestDTO;
import com.abc123.accounting.service.BalanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 余额控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class BalanceControllerTest {

    @Mock
    private BalanceService balanceService;

    @Test
    void shouldReturnBalanceDetail() {
        BalanceController controller = new BalanceController(balanceService);

        controller.detail("ACT10001");

        verify(balanceService).detail("ACT10001");
    }

    @Test
    void shouldCreditBalance() {
        BalanceController controller = new BalanceController(balanceService);
        BalanceOperationRequestDTO request = new BalanceOperationRequestDTO();

        controller.credit(request);

        verify(balanceService).credit(request);
    }

    @Test
    void shouldFreezeBalance() {
        BalanceController controller = new BalanceController(balanceService);
        BalanceOperationRequestDTO request = new BalanceOperationRequestDTO();

        controller.freeze(request);

        verify(balanceService).freeze(request);
    }

    @Test
    void shouldUnfreezeBalance() {
        BalanceController controller = new BalanceController(balanceService);
        BalanceOperationRequestDTO request = new BalanceOperationRequestDTO();

        controller.unfreeze(request);

        verify(balanceService).unfreeze(request);
    }
}
