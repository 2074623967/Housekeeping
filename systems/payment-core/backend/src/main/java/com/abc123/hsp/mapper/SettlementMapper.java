package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import java.util.List;

public interface SettlementMapper {

    List<WorkerSettlementListItemDTO> findWorkerSettlements();
}
