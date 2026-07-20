package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.ClearingBatchDTO;
import com.abc123.clearing.dto.CreateClearingBatchRequestDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.RerunClearingBatchRequestDTO;
import com.abc123.clearing.service.ClearingBatchService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 清分批次服务实现。
 */
@Service
public class ClearingBatchServiceImpl implements ClearingBatchService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingMapper clearingMapper;

    public ClearingBatchServiceImpl(ClearingMemoryStore clearingMemoryStore, ClearingMapper clearingMapper) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingMapper = clearingMapper;
    }

    @Override
    public PageResultDTO<ClearingBatchDTO> list(String batchDate, String batchStatus, int pageNo, int pageSize) {
        List<ClearingBatchDTO> items = clearingMemoryStore.batches().stream()
                .filter(item -> batchDate == null || batchDate.isEmpty() || batchDate.equals(item.getBatchDate()))
                .filter(item -> batchStatus == null || batchStatus.isEmpty() || batchStatus.equals(item.getBatchStatus()))
                .map(clearingMapper::toBatchDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public ClearingBatchDTO create(CreateClearingBatchRequestDTO request) {
        return clearingMapper.toBatchDTO(clearingMemoryStore.createBatch(
                request.getBatchDate(),
                request.getSourceType(),
                request.getCreatedBy(),
                request.getIdempotencyKey()));
    }

    @Override
    public ClearingBatchDTO detail(String batchNo) {
        return clearingMapper.toBatchDTO(clearingMemoryStore.findBatch(batchNo));
    }

    @Override
    public ClearingBatchDTO rerun(String batchNo, RerunClearingBatchRequestDTO request) {
        return clearingMapper.toBatchDTO(clearingMemoryStore.rerunBatch(batchNo, request.getOperatorName(), request.getReason()));
    }

    private PageResultDTO<ClearingBatchDTO> page(List<ClearingBatchDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
