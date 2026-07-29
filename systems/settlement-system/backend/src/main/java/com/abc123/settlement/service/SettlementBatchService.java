package com.abc123.settlement.service;

import com.abc123.settlement.dto.CreateSettlementBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementBatchDTO;

/**
 * 结算批次服务。
 */
public interface SettlementBatchService {

    PageResultDTO<SettlementBatchDTO> list(String batchDate, String batchStatus, int pageNo, int pageSize);

    SettlementBatchDTO create(CreateSettlementBatchRequestDTO request);

    SettlementBatchDTO detail(String batchNo);
}
