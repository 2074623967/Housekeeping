package com.abc123.hsp.service;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import java.util.List;

public interface SettlementService {

    List<WorkerSettlementListItemDTO> workerList(WorkerSettlementQueryDTO query);
}
