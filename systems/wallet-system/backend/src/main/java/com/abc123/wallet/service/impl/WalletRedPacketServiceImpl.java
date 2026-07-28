package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletRedPacketDTO;
import com.abc123.wallet.dto.WalletRedPacketRequestDTO;
import com.abc123.wallet.entity.WalletAccountEntity;
import com.abc123.wallet.entity.WalletRedPacketEntity;
import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.service.WalletRedPacketService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletRedPacketServiceImpl implements WalletRedPacketService {

    private final WalletMapper walletMapper;

    public WalletRedPacketServiceImpl(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    @Override
    public List<WalletRedPacketDTO> listRedPackets() {
        List<WalletRedPacketEntity> entities = walletMapper.findRedPackets();
        List<WalletRedPacketDTO> results = new ArrayList<>();
        for (WalletRedPacketEntity entity : entities) {
            results.add(toDTO(entity));
        }
        return results;
    }

    @Transactional
    @Override
    public WalletRedPacketDTO issue(WalletRedPacketRequestDTO request) {
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("红包总金额必须大于 0");
        }
        if (request.getPacketCount() == null || request.getPacketCount() <= 0) {
            throw new IllegalArgumentException("红包个数必须大于 0");
        }
        WalletAccountEntity account = walletMapper.findAccountByNo(request.getAccountNo());
        if (account == null) {
            throw new IllegalArgumentException("钱包账户不存在");
        }
        BigDecimal available = new BigDecimal(account.getAvailableAmount());
        if (available.compareTo(request.getTotalAmount()) < 0) {
            throw new IllegalArgumentException("营销资金余额不足");
        }
        String redPacketNo = "RED" + new Date().getTime();
        walletMapper.insertRedPacket(
                redPacketNo,
                request.getAccountNo(),
                request.getCampaignName(),
                request.getTotalAmount(),
                request.getPacketCount(),
                "ISSUED");
        walletMapper.updateAccountAmount(request.getAccountNo(), request.getTotalAmount().negate());
        walletMapper.insertLedger(
                "LED" + new Date().getTime(),
                request.getAccountNo(),
                "RED_PACKET_OUT",
                redPacketNo,
                request.getTotalAmount(),
                "OUT");
        List<WalletRedPacketEntity> entities = walletMapper.findRedPackets();
        for (WalletRedPacketEntity entity : entities) {
            if (redPacketNo.equals(entity.getRedPacketNo())) {
                return toDTO(entity);
            }
        }
        throw new IllegalArgumentException("红包批次创建失败");
    }

    private WalletRedPacketDTO toDTO(WalletRedPacketEntity entity) {
        WalletRedPacketDTO dto = new WalletRedPacketDTO();
        dto.setRedPacketNo(entity.getRedPacketNo());
        dto.setAccountNo(entity.getAccountNo());
        dto.setCampaignName(entity.getCampaignName());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setPacketCount(entity.getPacketCount());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
