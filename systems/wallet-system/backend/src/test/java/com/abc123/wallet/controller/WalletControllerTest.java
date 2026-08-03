package com.abc123.wallet.controller;

import static org.mockito.Mockito.verify;

import com.abc123.wallet.dto.WalletBalancePaymentRequestDTO;
import com.abc123.wallet.dto.WalletRechargeRequestDTO;
import com.abc123.wallet.dto.WalletTransferRequestDTO;
import com.abc123.wallet.dto.WalletWithdrawRequestDTO;
import com.abc123.wallet.service.WalletBalancePaymentService;
import com.abc123.wallet.service.WalletRechargeService;
import com.abc123.wallet.service.WalletService;
import com.abc123.wallet.service.WalletTransferService;
import com.abc123.wallet.service.WalletWithdrawService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 钱包账户控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private WalletBalancePaymentService walletBalancePaymentService;

    @Mock
    private WalletRechargeService walletRechargeService;

    @Mock
    private WalletTransferService walletTransferService;

    @Mock
    private WalletWithdrawService walletWithdrawService;

    @Test
    void shouldListWalletAccounts() {
        WalletController controller = controller();

        controller.list();

        verify(walletService).listAccounts();
    }

    @Test
    void shouldListWalletLedgers() {
        WalletController controller = controller();

        controller.ledgers("WALLET-001", "BALANCE_PAYMENT", "OUT");

        verify(walletService).listLedgers("WALLET-001", "BALANCE_PAYMENT", "OUT");
    }

    @Test
    void shouldReturnWalletAccountDetail() {
        WalletController controller = controller();

        controller.detail("WALLET-001");

        verify(walletService).getDetail("WALLET-001");
    }

    @Test
    void shouldCreateBalancePayment() {
        WalletController controller = controller();
        WalletBalancePaymentRequestDTO request = new WalletBalancePaymentRequestDTO();

        controller.balancePayment(request);

        verify(walletBalancePaymentService).pay(request);
    }

    @Test
    void shouldCreateRechargeOrder() {
        WalletController controller = controller();
        WalletRechargeRequestDTO request = new WalletRechargeRequestDTO();

        controller.recharge(request);

        verify(walletRechargeService).recharge(request);
    }

    @Test
    void shouldCreateWithdrawOrder() {
        WalletController controller = controller();
        WalletWithdrawRequestDTO request = new WalletWithdrawRequestDTO();

        controller.withdraw(request);

        verify(walletWithdrawService).withdraw(request);
    }

    @Test
    void shouldCreateTransferOrder() {
        WalletController controller = controller();
        WalletTransferRequestDTO request = new WalletTransferRequestDTO();

        controller.transfer(request);

        verify(walletTransferService).transfer(request);
    }

    private WalletController controller() {
        return new WalletController(
                walletService,
                walletBalancePaymentService,
                walletRechargeService,
                walletTransferService,
                walletWithdrawService);
    }
}
