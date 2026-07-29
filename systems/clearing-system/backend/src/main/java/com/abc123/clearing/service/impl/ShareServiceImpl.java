package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.ShareItemDTO;
import com.abc123.clearing.service.ShareService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 分账明细服务实现。
 */
@Service
public class ShareServiceImpl implements ShareService {

    private final ClearingMemoryStore clearingMemoryStore;
    private final ClearingMapper clearingMapper;

    public ShareServiceImpl(ClearingMemoryStore clearingMemoryStore, ClearingMapper clearingMapper) {
        this.clearingMemoryStore = clearingMemoryStore;
        this.clearingMapper = clearingMapper;
    }

    @Override
    public PageResultDTO<ShareItemDTO> list(String clearingNo, String shareType, int pageNo, int pageSize) {
        List<ShareItemDTO> items = clearingMemoryStore.shares().stream()
                .filter(item -> clearingNo == null || clearingNo.isEmpty() || clearingNo.equals(item.getClearingNo()))
                .filter(item -> shareType == null || shareType.isEmpty() || shareType.equals(item.getShareType()))
                .map(clearingMapper::toShareItemDTO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    private PageResultDTO<ShareItemDTO> page(List<ShareItemDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
