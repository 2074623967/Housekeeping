package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.CreateSettlementBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementBatchDTO;
import com.abc123.settlement.service.SettlementBatchService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 结算批次服务实现。
 */
@Service
public class SettlementBatchServiceImpl implements SettlementBatchService {

    private final SettlementMemoryStore settlementMemoryStore;
    private final SettlementMapper settlementMapper;

    public SettlementBatchServiceImpl(SettlementMemoryStore settlementMemoryStore, SettlementMapper settlementMapper) {
        this.settlementMemoryStore = settlementMemoryStore;
        this.settlementMapper = settlementMapper;
    }

    @Override
    public PageResultDTO<SettlementBatchDTO> list(String batchDate, String batchStatus, int pageNo, int pageSize) {
        List<SettlementBatchDTO> items = settlementMemoryStore.batches().stream()
                .filter(item -> batchDate == null || batchDate.isEmpty() || batchDate.equals(item.getBatchDate()))
                .filter(item -> batchStatus == null || batchStatus.isEmpty() || batchStatus.equals(item.getBatchStatus()))
                .map(settlementMapper::toBatchDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public SettlementBatchDTO create(CreateSettlementBatchRequestDTO request) {
        return settlementMapper.toBatchDTO(settlementMemoryStore.createBatch(
                request.getBatchDate(),
                request.getSettlementType(),
                request.getCreatedBy(),
                request.getIdempotencyKey()));
    }

    @Override
    public SettlementBatchDTO detail(String batchNo) {
        return settlementMapper.toBatchDTO(settlementMemoryStore.findBatch(batchNo));
    }

    private PageResultDTO<SettlementBatchDTO> page(List<SettlementBatchDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
