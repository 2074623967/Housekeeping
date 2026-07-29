package com.abc123.clearing.service;

import com.abc123.clearing.dto.ClearingBatchDTO;
import com.abc123.clearing.dto.CreateClearingBatchRequestDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.RerunClearingBatchRequestDTO;

/**
 * 清分批次服务。
 */
public interface ClearingBatchService {

    PageResultDTO<ClearingBatchDTO> list(String batchDate, String batchStatus, int pageNo, int pageSize);

    ClearingBatchDTO create(CreateClearingBatchRequestDTO request);

    ClearingBatchDTO detail(String batchNo);

    ClearingBatchDTO rerun(String batchNo, RerunClearingBatchRequestDTO request);
}
