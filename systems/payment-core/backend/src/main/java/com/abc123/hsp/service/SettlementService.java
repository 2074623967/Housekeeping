package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;

public interface SettlementService {

    PageResultDTO<WorkerSettlementListItemDTO> workerList(WorkerSettlementQueryDTO query);
}
