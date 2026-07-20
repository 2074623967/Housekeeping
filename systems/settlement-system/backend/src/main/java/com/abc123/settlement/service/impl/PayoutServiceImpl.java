package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.CreatePayoutBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.PayoutRecordDTO;
import com.abc123.settlement.dto.RetryPayoutBatchRequestDTO;
import com.abc123.settlement.service.PayoutService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 出款服务实现。
 */
@Service
public class PayoutServiceImpl implements PayoutService {

    private final SettlementMemoryStore settlementMemoryStore;
    private final SettlementMapper settlementMapper;

    public PayoutServiceImpl(SettlementMemoryStore settlementMemoryStore, SettlementMapper settlementMapper) {
        this.settlementMemoryStore = settlementMemoryStore;
        this.settlementMapper = settlementMapper;
    }

    @Override
    public PageResultDTO<PayoutBatchDTO> list(String batchNo, String payoutStatus, int pageNo, int pageSize) {
        List<PayoutBatchDTO> items = settlementMemoryStore.payoutBatches().stream()
                .filter(item -> batchNo == null || batchNo.isEmpty() || batchNo.equals(item.getBatchNo()))
                .filter(item -> payoutStatus == null || payoutStatus.isEmpty() || payoutStatus.equals(item.getPayoutStatus()))
                .map(settlementMapper::toPayoutBatchDTO)
                .collect(Collectors.toList());
        return pageBatches(items, pageNo, pageSize);
    }

    @Override
    public PayoutBatchDTO create(CreatePayoutBatchRequestDTO request) {
        PayoutBatchDTO dto = settlementMapper.toPayoutBatchDTO(
                settlementMemoryStore.createPayoutBatch(request.getBatchNo(), request.getPayoutChannel(), request.getCreatedBy()));
        settlementMemoryStore.orders().stream()
                .filter(order -> request.getBatchNo().equals(order.getBatchNo()))
                .filter(order -> "待出款".equals(order.getPayoutStatus()))
                .forEach(order -> {
                    settlementMemoryStore.createPayoutRecord(dto.getPayoutBatchNo(), order);
                    order.setPayoutStatus("已发放");
                    order.setSettlementStatus("已出款");
                });
        return settlementMapper.toPayoutBatchDTO(settlementMemoryStore.findPayoutBatch(dto.getPayoutBatchNo()));
    }

    @Override
    public PayoutBatchDTO retry(String payoutBatchNo, RetryPayoutBatchRequestDTO request) {
        return settlementMapper.toPayoutBatchDTO(settlementMemoryStore.retryPayoutBatch(payoutBatchNo, request.getOperatorName(), request.getReason()));
    }

    @Override
    public PageResultDTO<PayoutRecordDTO> records(String payoutBatchNo, String payoutStatus, int pageNo, int pageSize) {
        List<PayoutRecordDTO> items = settlementMemoryStore.payoutRecordsByBatchNo(payoutBatchNo).stream()
                .filter(item -> payoutStatus == null || payoutStatus.isEmpty() || payoutStatus.equals(item.getPayoutStatus()))
                .map(settlementMapper::toPayoutRecordDTO)
                .collect(Collectors.toList());
        return pageRecords(items, pageNo, pageSize);
    }

    private PageResultDTO<PayoutBatchDTO> pageBatches(List<PayoutBatchDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    private PageResultDTO<PayoutRecordDTO> pageRecords(List<PayoutRecordDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
