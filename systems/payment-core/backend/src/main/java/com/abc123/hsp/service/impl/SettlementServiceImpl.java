package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.mapper.SettlementMapper;
import com.abc123.hsp.service.SettlementService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SettlementServiceImpl implements SettlementService {

    private final SettlementMapper settlementMapper;

    public SettlementServiceImpl(SettlementMapper settlementMapper) {
        this.settlementMapper = settlementMapper;
    }

    @Override
    public List<WorkerSettlementListItemDTO> workerList(WorkerSettlementQueryDTO query) {
        return settlementMapper.findWorkerSettlements(query);
    }
}
