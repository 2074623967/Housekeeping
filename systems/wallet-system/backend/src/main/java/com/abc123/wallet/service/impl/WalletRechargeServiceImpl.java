package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletRechargeOrderDTO;
import com.abc123.wallet.dto.WalletRechargeRequestDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletRechargeOrderEntity;
import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.service.WalletRechargeService;
import java.math.BigDecimal;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletRechargeServiceImpl implements WalletRechargeService {

    private final WalletMapper walletMapper;

    public WalletRechargeServiceImpl(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    @Transactional
    @Override
    public WalletRechargeOrderDTO recharge(WalletRechargeRequestDTO request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("充值金额必须大于 0");
        }
        WalletAccountEntity account = walletMapper.findAccountByNo(request.getAccountNo());
        if (account == null) {
            throw new IllegalArgumentException("钱包账户不存在");
        }
        String rechargeNo = "RCH" + new Date().getTime();
        walletMapper.insertRechargeOrder(rechargeNo, request.getAccountNo(), request.getBizNo(), request.getAmount(), "SUCCESS");
        walletMapper.updateAccountAmount(request.getAccountNo(), request.getAmount());
        walletMapper.insertLedger("LED" + new Date().getTime(), request.getAccountNo(), "RECHARGE", request.getBizNo(), request.getAmount(), "IN");
        WalletRechargeOrderEntity entity = walletMapper.findRechargeOrderByNo(rechargeNo);
        return toDTO(entity);
    }

    private WalletRechargeOrderDTO toDTO(WalletRechargeOrderEntity entity) {
        WalletRechargeOrderDTO dto = new WalletRechargeOrderDTO();
        dto.setRechargeNo(entity.getRechargeNo());
        dto.setAccountNo(entity.getAccountNo());
        dto.setBizNo(entity.getBizNo());
        dto.setAmount(entity.getAmount());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
