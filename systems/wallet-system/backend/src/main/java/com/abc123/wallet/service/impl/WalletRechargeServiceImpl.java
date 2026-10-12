package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletRechargeOrderDTO;
import com.abc123.wallet.dto.WalletRechargeRequestDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletRechargeOrderEntity;
import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.service.WalletRechargeService;
import java.math.BigDecimal;
import java.util.UUID;
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
        if (request == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || request.getAmount().scale() > 2) {
            throw new IllegalArgumentException("充值金额必须大于 0");
        }
        if (request.getBizNo() == null || request.getBizNo().trim().isEmpty() || request.getBizNo().length() > 64) {
            throw new IllegalArgumentException("充值业务单号不能为空且不能超过 64 字符");
        }
        WalletAccountEntity account = walletMapper.findAccountByNo(request.getAccountNo());
        if (account == null) {
            throw new IllegalArgumentException("钱包账户不存在");
        }
        WalletRechargeOrderEntity existing = walletMapper.findRechargeOrderByBizNo(request.getBizNo());
        if (existing != null) {
            if (!existing.getAccountNo().equals(request.getAccountNo())
                    || new BigDecimal(existing.getAmount()).compareTo(request.getAmount()) != 0) {
                throw new IllegalArgumentException("充值业务单号已被其他请求使用");
            }
            return toDTO(existing);
        }
        String rechargeNo = "RCH" + UUID.randomUUID().toString().replace("-", "");
        walletMapper.insertRechargeOrder(rechargeNo, request.getAccountNo(), request.getBizNo(), request.getAmount(), "PENDING");
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
