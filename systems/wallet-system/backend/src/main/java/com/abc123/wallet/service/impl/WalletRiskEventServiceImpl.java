package com.abc123.wallet.service.impl;

import com.abc123.wallet.dto.WalletRiskApprovalRequestDTO;
import com.abc123.wallet.dto.WalletRiskEventDTO;
import com.abc123.wallet.entity.WalletRiskEventEntity;
import com.abc123.wallet.entity.WalletRedPacketEntity;
import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.service.WalletRiskEventService;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletRiskEventServiceImpl implements WalletRiskEventService {

    private final WalletMapper walletMapper;

    public WalletRiskEventServiceImpl(WalletMapper walletMapper) {
        this.walletMapper = walletMapper;
    }

    @Override
    public List<WalletRiskEventDTO> listRiskEvents() {
        List<WalletRiskEventDTO> results = new ArrayList<>();
        for (WalletRiskEventEntity entity : walletMapper.findRiskEvents()) {
            results.add(toDTO(entity));
        }
        return results;
    }

    @Transactional
    @Override
    public WalletRiskEventDTO approve(WalletRiskApprovalRequestDTO request) {
        if (request.getEventNo() == null || request.getEventNo().trim().isEmpty()) {
            throw new IllegalArgumentException("风控事件号不能为空");
        }
        WalletRiskEventEntity event = walletMapper.findRiskEventByNo(request.getEventNo());
        if (event == null) {
            throw new IllegalArgumentException("风控事件不存在");
        }
        if (!"PENDING".equals(event.getStatus())) {
            throw new IllegalArgumentException("当前风控事件无需重复审批");
        }
        if ("REJECTED".equals(request.getAction())) {
            walletMapper.updateRiskEvent(request.getEventNo(), "REJECTED", request.getHandledBy(), request.getHandledRemark());
            if ("RED_PACKET".equals(event.getBizType())) {
                walletMapper.updateRedPacketStatus(event.getBizNo(), "REJECTED");
            }
            return toDTO(walletMapper.findRiskEventByNo(request.getEventNo()));
        }
        walletMapper.updateRiskEvent(request.getEventNo(), "APPROVED", request.getHandledBy(), request.getHandledRemark());
        if ("RED_PACKET".equals(event.getBizType())) {
            WalletRedPacketEntity redPacket = walletMapper.findRedPacketByNo(event.getBizNo());
            if (redPacket != null && "PENDING_APPROVAL".equals(redPacket.getStatus())) {
                walletMapper.updateRedPacketStatus(redPacket.getRedPacketNo(), "ISSUED");
                walletMapper.updateAccountAmount(redPacket.getAccountNo(), new BigDecimal(redPacket.getTotalAmount()).negate());
                walletMapper.insertLedger(
                        "LED" + System.currentTimeMillis(),
                        redPacket.getAccountNo(),
                        "RED_PACKET_OUT",
                        redPacket.getRedPacketNo(),
                        new BigDecimal(redPacket.getTotalAmount()),
                        "OUT");
            }
        }
        return toDTO(walletMapper.findRiskEventByNo(request.getEventNo()));
    }

    private WalletRiskEventDTO toDTO(WalletRiskEventEntity entity) {
        WalletRiskEventDTO dto = new WalletRiskEventDTO();
        dto.setEventNo(entity.getEventNo());
        dto.setBizType(entity.getBizType());
        dto.setBizNo(entity.getBizNo());
        dto.setRiskLevel(entity.getRiskLevel());
        dto.setStatus(entity.getStatus());
        dto.setRiskReason(entity.getRiskReason());
        dto.setHandledBy(entity.getHandledBy());
        dto.setHandledRemark(entity.getHandledRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setHandledAt(entity.getHandledAt());
        return dto;
    }
}
