package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.mapper.SettlementMapper;
import com.abc123.hsp.service.SettlementService;
import org.springframework.stereotype.Service;

@Service
public class SettlementServiceImpl implements SettlementService {

    private final SettlementMapper settlementMapper;

    public SettlementServiceImpl(SettlementMapper settlementMapper) {
        this.settlementMapper = settlementMapper;
    }

    @Override
    public PageResultDTO<WorkerSettlementListItemDTO> workerList(WorkerSettlementQueryDTO query) {
        query.setSettlementOrderId(query.getSettlementOrderId() == null ? null : query.getSettlementOrderId().trim());
        query.setWorkerKeyword(query.getWorkerKeyword() == null ? null : query.getWorkerKeyword().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                settlementMapper.findWorkerSettlements(query),
                settlementMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }
}
