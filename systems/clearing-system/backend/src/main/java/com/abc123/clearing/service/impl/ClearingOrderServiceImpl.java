package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.ClearingOrderDTO;
import com.abc123.clearing.dto.ClearingOrderDetailDTO;
import com.abc123.clearing.dto.FeeRuleDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.ShareItemDTO;
import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.service.ClearingOrderService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 清分结果服务实现。
 */
@Service
public class ClearingOrderServiceImpl implements ClearingOrderService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingMapper clearingMapper;

    public ClearingOrderServiceImpl(ClearingMemoryStore clearingMemoryStore, ClearingMapper clearingMapper) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingMapper = clearingMapper;
    }

    @Override
    public PageResultDTO<ClearingOrderDTO> list(String batchNo, String orderNo, String clearingStatus, int pageNo, int pageSize) {
        List<ClearingOrderDTO> items = clearingMemoryStore.orders().stream()
                .filter(item -> batchNo == null || batchNo.isEmpty() || batchNo.equals(item.getBatchNo()))
                .filter(item -> orderNo == null || orderNo.isEmpty() || orderNo.equals(item.getOrderNo()))
                .filter(item -> clearingStatus == null || clearingStatus.isEmpty() || clearingStatus.equals(item.getClearingStatus()))
                .map(clearingMapper::toOrderDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public ClearingOrderDetailDTO detail(String clearingNo) {
        ClearingOrderEntity order = clearingMemoryStore.findOrder(clearingNo);
        List<ShareItemDTO> shareItems = clearingMemoryStore.sharesByClearingNo(clearingNo).stream()
                .map(clearingMapper::toShareItemDTO)
                .collect(Collectors.toList());
        List<FeeRuleDTO> feeRules = clearingMemoryStore.feeRules().stream()
                .map(clearingMapper::toFeeRuleDTO)
                .collect(Collectors.toList());
        return clearingMapper.toOrderDetailDTO(order, shareItems, feeRules);
    }

    private PageResultDTO<ClearingOrderDTO> page(List<ClearingOrderDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
