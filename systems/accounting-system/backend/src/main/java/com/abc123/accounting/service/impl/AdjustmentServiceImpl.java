package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.AdjustmentItemDTO;
import com.abc123.accounting.dto.ApproveAdjustmentRequestDTO;
import com.abc123.accounting.dto.CreateAdjustmentRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.service.AdjustmentService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 调账服务实现。
 */
@Service
public class AdjustmentServiceImpl implements AdjustmentService {

    private final AccountingMemoryStore accountingMemoryStore;
    private final AccountingMapper accountingMapper;

    public AdjustmentServiceImpl(AccountingMemoryStore accountingMemoryStore, AccountingMapper accountingMapper) {
        this.accountingMemoryStore = accountingMemoryStore;
        this.accountingMapper = accountingMapper;
    }

    @Override
    public PageResultDTO<AdjustmentItemDTO> list(String accountNo, String adjustStatus, int pageNo, int pageSize) {
        List<AdjustmentItemDTO> items = accountingMemoryStore.adjustments().stream()
                .filter(item -> accountNo == null || accountNo.isEmpty() || accountNo.equals(item.getAccountNo()))
                .filter(item -> adjustStatus == null || adjustStatus.isEmpty() || adjustStatus.equals(item.getAdjustStatus()))
                .map(accountingMapper::toAdjustmentDTO)
                .collect(Collectors.toList());
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    @Override
    public AdjustmentItemDTO create(CreateAdjustmentRequestDTO request) {
        return accountingMapper.toAdjustmentDTO(accountingMemoryStore.createAdjustment(
                request.getAccountNo(),
                request.getAdjustDirection(),
                request.getAdjustAmount(),
                request.getAdjustReason(),
                request.getCreatedBy()));
    }

    @Override
    public AdjustmentItemDTO approve(String adjustNo, ApproveAdjustmentRequestDTO request) {
        return accountingMapper.toAdjustmentDTO(accountingMemoryStore.approveAdjustment(adjustNo, request.getApprovedBy()));
    }
}
