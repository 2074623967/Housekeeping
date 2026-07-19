package com.abc123.hsp.service;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.repository.SettlementRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;

    public SettlementService(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    public List<WorkerSettlementListItemDTO> workerList() {
        return settlementRepository.findWorkerSettlements();
    }
}
