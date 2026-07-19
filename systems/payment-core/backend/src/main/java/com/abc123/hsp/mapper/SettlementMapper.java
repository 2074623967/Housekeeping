package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import java.util.List;

public interface SettlementMapper {

    List<WorkerSettlementListItemDTO> findWorkerSettlements(WorkerSettlementQueryDTO query);

    long count(WorkerSettlementQueryDTO query);
}
