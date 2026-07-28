package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletBalancePaymentOrderDTO;
import com.abc123.wallet.dto.WalletBalancePaymentRequestDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletBalancePaymentOrderEntity;
import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.service.WalletBalancePaymentService;
import java.math.BigDecimal;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletBalancePaymentServiceImpl implements WalletBalancePaymentService {

    private final WalletMapper walletMapper;

    public WalletBalancePaymentServiceImpl(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    @Transactional
    @Override
    public WalletBalancePaymentOrderDTO pay(WalletBalancePaymentRequestDTO request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("余额支付金额必须大于 0");
        }
        WalletAccountEntity account = walletMapper.findAccountByNo(request.getAccountNo());
        if (account == null) {
            throw new IllegalArgumentException("钱包账户不存在");
        }
        BigDecimal available = new BigDecimal(account.getAvailableAmount());
        if (available.compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException("钱包余额不足");
        }
        String balancePaymentNo = "WBP" + new Date().getTime();
        walletMapper.insertBalancePaymentOrder(balancePaymentNo, request.getAccountNo(), request.getBizNo(), request.getAmount(), "SUCCESS");
        walletMapper.updateAccountAmount(request.getAccountNo(), request.getAmount().negate());
        walletMapper.insertLedger("LED" + new Date().getTime(), request.getAccountNo(), "BALANCE_PAY", request.getBizNo(), request.getAmount(), "OUT");
        WalletBalancePaymentOrderEntity entity = walletMapper.findBalancePaymentOrderByNo(balancePaymentNo);
        return toDTO(entity);
    }

    private WalletBalancePaymentOrderDTO toDTO(WalletBalancePaymentOrderEntity entity) {
        WalletBalancePaymentOrderDTO dto = new WalletBalancePaymentOrderDTO();
        dto.setBalancePaymentNo(entity.getBalancePaymentNo());
        dto.setAccountNo(entity.getAccountNo());
        dto.setBizNo(entity.getBizNo());
        dto.setAmount(entity.getAmount());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
