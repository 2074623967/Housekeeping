package com.abc123.wallet.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.wallet.dto.WalletBalancePaymentRequestDTO;
import com.abc123.wallet.dto.WalletTransferRequestDTO;
import com.abc123.wallet.dto.WalletWithdrawRequestDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletBalancePaymentOrderEntity;
import com.abc123.wallet.entity.WalletTransferOrderEntity;
import com.abc123.wallet.mapper.WalletMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 钱包核心资金动作单测，覆盖余额支付、转账、提现的余额校验和记账调用。
 */
class WalletCoreFundsServiceImplTest {

    @Test
    void shouldDeductBalanceAndCreateBalancePaymentWhenFundsAreSufficient() {
        WalletMapper walletMapper = mock(WalletMapper.class);
        WalletBalancePaymentServiceImpl service = new WalletBalancePaymentServiceImpl(walletMapper);
        WalletBalancePaymentRequestDTO request = new WalletBalancePaymentRequestDTO();
        request.setAccountNo("WALLET-001");
        request.setBizNo("ORDER-001");
        request.setAmount(new BigDecimal("88.00"));
        when(walletMapper.findAccountByNo("WALLET-001")).thenReturn(createAccount("WALLET-001", "100.00"));
        when(walletMapper.findBalancePaymentOrderByNo(anyString())).thenReturn(createBalancePaymentOrder());

        service.pay(request);

        verify(walletMapper).insertBalancePaymentOrder(
                anyString(), eq("WALLET-001"), eq("ORDER-001"), eq(new BigDecimal("88.00")), eq("SUCCESS"));
        verify(walletMapper).updateAccountAmount("WALLET-001", new BigDecimal("-88.00"));
        verify(walletMapper).insertLedger(
                anyString(), eq("WALLET-001"), eq("BALANCE_PAY"), eq("ORDER-001"), eq(new BigDecimal("88.00")), eq("OUT"));
    }

    @Test
    void shouldRejectBalancePaymentWhenFundsAreInsufficient() {
        WalletMapper walletMapper = mock(WalletMapper.class);
        WalletBalancePaymentServiceImpl service = new WalletBalancePaymentServiceImpl(walletMapper);
        WalletBalancePaymentRequestDTO request = new WalletBalancePaymentRequestDTO();
        request.setAccountNo("WALLET-001");
        request.setBizNo("ORDER-001");
        request.setAmount(new BigDecimal("101.00"));
        when(walletMapper.findAccountByNo("WALLET-001")).thenReturn(createAccount("WALLET-001", "100.00"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.pay(request));

        assertEquals("钱包余额不足", error.getMessage());
        verify(walletMapper, never()).insertBalancePaymentOrder(
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(BigDecimal.class), anyString());
        verify(walletMapper, never()).updateAccountAmount(anyString(), org.mockito.ArgumentMatchers.any(BigDecimal.class));
    }

    @Test
    void shouldCreateDoubleEntryWhenTransferIsValid() {
        WalletMapper walletMapper = mock(WalletMapper.class);
        WalletTransferServiceImpl service = new WalletTransferServiceImpl(walletMapper);
        WalletTransferRequestDTO request = new WalletTransferRequestDTO();
        request.setSourceAccountNo("WALLET-001");
        request.setTargetAccountNo("WALLET-002");
        request.setBizNo("TRANSFER-001");
        request.setAmount(new BigDecimal("50.00"));
        when(walletMapper.findAccountByNo("WALLET-001")).thenReturn(createAccount("WALLET-001", "100.00"));
        when(walletMapper.findAccountByNo("WALLET-002")).thenReturn(createAccount("WALLET-002", "10.00"));
        when(walletMapper.findTransferOrderByNo(anyString())).thenReturn(createTransferOrder());

        service.transfer(request);

        verify(walletMapper).updateAccountAmount("WALLET-001", new BigDecimal("-50.00"));
        verify(walletMapper).updateAccountAmount("WALLET-002", new BigDecimal("50.00"));
        verify(walletMapper).insertLedger(
                anyString(), eq("WALLET-001"), eq("TRANSFER_OUT"), eq("TRANSFER-001"), eq(new BigDecimal("50.00")), eq("OUT"));
        verify(walletMapper).insertLedger(
                anyString(), eq("WALLET-002"), eq("TRANSFER_IN"), eq("TRANSFER-001"), eq(new BigDecimal("50.00")), eq("IN"));
    }

    @Test
    void shouldRejectTransferWhenSourceAndTargetAreSame() {
        WalletMapper walletMapper = mock(WalletMapper.class);
        WalletTransferServiceImpl service = new WalletTransferServiceImpl(walletMapper);
        WalletTransferRequestDTO request = new WalletTransferRequestDTO();
        request.setSourceAccountNo("WALLET-001");
        request.setTargetAccountNo("WALLET-001");
        request.setBizNo("TRANSFER-001");
        request.setAmount(new BigDecimal("50.00"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.transfer(request));

        assertEquals("转出账户和转入账户不能相同", error.getMessage());
        verify(walletMapper, never()).insertTransferOrder(
                anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(BigDecimal.class), anyString());
    }

    @Test
    void shouldRejectWithdrawWhenFundsAreInsufficient() {
        WalletMapper walletMapper = mock(WalletMapper.class);
        WalletWithdrawServiceImpl service = new WalletWithdrawServiceImpl(walletMapper);
        WalletWithdrawRequestDTO request = new WalletWithdrawRequestDTO();
        request.setAccountNo("WALLET-001");
        request.setBizNo("WITHDRAW-001");
        request.setAmount(new BigDecimal("101.00"));
        when(walletMapper.findAccountByNo("WALLET-001")).thenReturn(createAccount("WALLET-001", "100.00"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.withdraw(request));

        assertEquals("余额不足", error.getMessage());
        verify(walletMapper, never()).insertWithdrawOrder(
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(BigDecimal.class), anyString());
    }

    private WalletAccountEntity createAccount(String accountNo, String availableAmount) {
        WalletAccountEntity account = new WalletAccountEntity();
        account.setAccountNo(accountNo);
        account.setAvailableAmount(availableAmount);
        return account;
    }

    private WalletBalancePaymentOrderEntity createBalancePaymentOrder() {
        WalletBalancePaymentOrderEntity order = new WalletBalancePaymentOrderEntity();
        order.setBalancePaymentNo("WBP-001");
        order.setAccountNo("WALLET-001");
        order.setBizNo("ORDER-001");
        order.setAmount("88.00");
        order.setStatus("SUCCESS");
        return order;
    }

    private WalletTransferOrderEntity createTransferOrder() {
        WalletTransferOrderEntity order = new WalletTransferOrderEntity();
        order.setTransferNo("TRF-001");
        order.setSourceAccountNo("WALLET-001");
        order.setTargetAccountNo("WALLET-002");
        order.setBizNo("TRANSFER-001");
        order.setAmount("50.00");
        order.setStatus("SUCCESS");
        return order;
    }
}
