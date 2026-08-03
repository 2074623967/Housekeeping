package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;

import com.abc123.accounting.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 账务流水控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class LedgerControllerTest {

    @Mock
    private LedgerService ledgerService;

    @Test
    void shouldListLedgers() {
        LedgerController controller = new LedgerController(ledgerService);

        controller.list("ACT10001", "PAY202608030001", "PAYMENT_SUCCESS", 1, 20);

        verify(ledgerService).list("ACT10001", "PAY202608030001", "PAYMENT_SUCCESS", 1, 20);
    }
}
