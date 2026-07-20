package com.abc123.accounting.service;

import com.abc123.accounting.dto.AdjustmentItemDTO;
import com.abc123.accounting.dto.ApproveAdjustmentRequestDTO;
import com.abc123.accounting.dto.CreateAdjustmentRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;

/**
 * 调账服务。
 */
public interface AdjustmentService {

    PageResultDTO<AdjustmentItemDTO> list(String accountNo, String adjustStatus, int pageNo, int pageSize);

    AdjustmentItemDTO create(CreateAdjustmentRequestDTO request);

    AdjustmentItemDTO approve(String adjustNo, ApproveAdjustmentRequestDTO request);
}
