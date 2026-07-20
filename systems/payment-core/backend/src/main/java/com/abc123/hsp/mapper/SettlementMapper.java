package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SettlementMapper {

    List<WorkerSettlementListItemDTO> findWorkerSettlements(@Param("query") WorkerSettlementQueryDTO query);

    long count(@Param("query") WorkerSettlementQueryDTO query);
}
