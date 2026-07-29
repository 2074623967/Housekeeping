package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletTransferOrderDTO;
import com.abc123.wallet.dto.WalletTransferRequestDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletTransferOrderEntity;
import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.service.WalletTransferService;
import java.math.BigDecimal;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletTransferServiceImpl implements WalletTransferService {

    private final WalletMapper walletMapper;

    public WalletTransferServiceImpl(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    @Transactional
    @Override
    public WalletTransferOrderDTO transfer(WalletTransferRequestDTO request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("转账金额必须大于 0");
        }
        if (request.getSourceAccountNo() == null || request.getTargetAccountNo() == null
                || request.getSourceAccountNo().trim().isEmpty() || request.getTargetAccountNo().trim().isEmpty()) {
            throw new IllegalArgumentException("转出账户和转入账户不能为空");
        }
        if (request.getSourceAccountNo().equals(request.getTargetAccountNo())) {
            throw new IllegalArgumentException("转出账户和转入账户不能相同");
        }
        WalletAccountEntity sourceAccount = walletMapper.findAccountByNo(request.getSourceAccountNo());
        WalletAccountEntity targetAccount = walletMapper.findAccountByNo(request.getTargetAccountNo());
        if (sourceAccount == null || targetAccount == null) {
            throw new IllegalArgumentException("钱包账户不存在");
        }
        BigDecimal available = new BigDecimal(sourceAccount.getAvailableAmount());
        if (available.compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException("转出账户余额不足");
        }
        String transferNo = "TRF" + new Date().getTime();
        walletMapper.insertTransferOrder(
                transferNo,
                request.getSourceAccountNo(),
                request.getTargetAccountNo(),
                request.getBizNo(),
                request.getAmount(),
                "SUCCESS");
        walletMapper.updateAccountAmount(request.getSourceAccountNo(), request.getAmount().negate());
        walletMapper.updateAccountAmount(request.getTargetAccountNo(), request.getAmount());
        walletMapper.insertLedger("LED" + new Date().getTime(),
                request.getSourceAccountNo(),
                "TRANSFER_OUT",
                request.getBizNo(),
                request.getAmount(),
                "OUT");
        walletMapper.insertLedger("LED" + (new Date().getTime() + 1),
                request.getTargetAccountNo(),
                "TRANSFER_IN",
                request.getBizNo(),
                request.getAmount(),
                "IN");
        WalletTransferOrderEntity entity = walletMapper.findTransferOrderByNo(transferNo);
        return toDTO(entity);
    }

    private WalletTransferOrderDTO toDTO(WalletTransferOrderEntity entity) {
        WalletTransferOrderDTO dto = new WalletTransferOrderDTO();
        dto.setTransferNo(entity.getTransferNo());
        dto.setSourceAccountNo(entity.getSourceAccountNo());
        dto.setTargetAccountNo(entity.getTargetAccountNo());
        dto.setBizNo(entity.getBizNo());
        dto.setAmount(entity.getAmount());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
