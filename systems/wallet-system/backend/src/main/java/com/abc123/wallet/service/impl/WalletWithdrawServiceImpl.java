package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletWithdrawOrderDTO;
import com.abc123.wallet.dto.WalletWithdrawRequestDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletWithdrawOrderEntity;
import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.service.WalletWithdrawService;
import java.math.BigDecimal;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletWithdrawServiceImpl implements WalletWithdrawService {

    private final WalletMapper walletMapper;

    public WalletWithdrawServiceImpl(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    @Transactional
    @Override
    public WalletWithdrawOrderDTO withdraw(WalletWithdrawRequestDTO request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("提现金额必须大于 0");
        }
        WalletAccountEntity account = walletMapper.findAccountByNo(request.getAccountNo());
        if (account == null) {
            throw new IllegalArgumentException("钱包账户不存在");
        }
        BigDecimal available = new BigDecimal(account.getAvailableAmount());
        if (available.compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException("余额不足");
        }
        String withdrawNo = "WTH" + new Date().getTime();
        walletMapper.insertWithdrawOrder(withdrawNo, request.getAccountNo(), request.getBizNo(), request.getAmount(), "SUCCESS");
        walletMapper.updateAccountAmount(request.getAccountNo(), request.getAmount().negate());
        walletMapper.insertLedger("LED" + new Date().getTime(), request.getAccountNo(), "WITHDRAW", request.getBizNo(), request.getAmount(), "OUT");
        WalletWithdrawOrderEntity entity = walletMapper.findWithdrawOrderByNo(withdrawNo);
        return toDTO(entity);
    }

    private WalletWithdrawOrderDTO toDTO(WalletWithdrawOrderEntity entity) {
        WalletWithdrawOrderDTO dto = new WalletWithdrawOrderDTO();
        dto.setWithdrawNo(entity.getWithdrawNo());
        dto.setAccountNo(entity.getAccountNo());
        dto.setBizNo(entity.getBizNo());
        dto.setAmount(entity.getAmount());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
