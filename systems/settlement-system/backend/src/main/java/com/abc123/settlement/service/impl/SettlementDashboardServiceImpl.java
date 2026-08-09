package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.SettlementBatchDTO;
import com.abc123.settlement.dto.SettlementDashboardDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
import com.abc123.settlement.service.SettlementDashboardService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 结算工作台服务实现。
 */
@Service
public class SettlementDashboardServiceImpl implements SettlementDashboardService {

    private final SettlementMemoryStore settlementMemoryStore;
    private final SettlementMapper settlementMapper;

    public SettlementDashboardServiceImpl(SettlementMemoryStore settlementMemoryStore, SettlementMapper settlementMapper) {
        this.settlementMemoryStore = settlementMemoryStore;
        this.settlementMapper = settlementMapper;
    }

    @Override
    public SettlementDashboardDTO dashboard() {
        List<SettlementBatchDTO> batches = settlementMemoryStore.batches().stream()
                .map(settlementMapper::toBatchDTO)
                .collect(Collectors.toList());
        List<SettlementOrderDTO> orders = settlementMemoryStore.orders().stream()
                .map(settlementMapper::toOrderDTO)
                .collect(Collectors.toList());
        List<PayoutBatchDTO> payouts = settlementMemoryStore.payoutBatches().stream()
                .map(settlementMapper::toPayoutBatchDTO)
                .collect(Collectors.toList());
        List<SettlementEventDTO> events = settlementMemoryStore.events().stream()
                .map(settlementMapper::toEventDTO)
                .collect(Collectors.toList());

        SettlementDashboardDTO dto = new SettlementDashboardDTO();
        dto.setBatchCount(batches.size());
        dto.setOrderCount(orders.size());
        dto.setPayoutCount(payouts.size());
        dto.setEventCount(events.size());
        dto.setPendingAuditCount((int) orders.stream().filter(item -> "待审核".equals(item.getAuditStatus())).count());
        dto.setRejectedCount((int) orders.stream().filter(item -> "已退回".equals(item.getAuditStatus())).count());
        dto.setFailedPayoutCount((int) payouts.stream().filter(this::isRiskPayout).count());
        dto.setPendingEventCount((int) events.stream().filter(item -> !"已消费".equals(item.getEventStatus())).count());
        dto.setDraftPayoutCount((int) payouts.stream().filter(item -> "待出款".equals(item.getPayoutStatus())).count());
        dto.setProcessingPayoutCount((int) payouts.stream().filter(item -> "处理中".equals(item.getPayoutStatus())).count());
        dto.setRecentBatches(batches.stream().limit(6).collect(Collectors.toList()));
        dto.setRecentOrders(orders.stream().limit(6).collect(Collectors.toList()));
        dto.setRiskPayouts(payouts.stream().filter(this::isRiskPayout).limit(6).collect(Collectors.toList()));
        dto.setRecentEvents(events.stream().limit(6).collect(Collectors.toList()));
        return dto;
    }

    private boolean isRiskPayout(PayoutBatchDTO item) {
        return item.getFailedCount() > 0
                || "部分失败".equals(item.getPayoutStatus())
                || "已失败".equals(item.getPayoutStatus());
    }
}
