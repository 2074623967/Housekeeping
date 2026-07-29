package com.abc123.settlement.service;

import com.abc123.settlement.dto.CreatePayoutBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.PayoutRecordDTO;
import com.abc123.settlement.dto.RetryPayoutBatchRequestDTO;

/**
 * 出款服务。
 */
public interface PayoutService {

    PageResultDTO<PayoutBatchDTO> list(String batchNo, String payoutStatus, int pageNo, int pageSize);

    PayoutBatchDTO create(CreatePayoutBatchRequestDTO request);

    PayoutBatchDTO retry(String payoutBatchNo, RetryPayoutBatchRequestDTO request);

    PageResultDTO<PayoutRecordDTO> records(String payoutBatchNo, String payoutStatus, int pageNo, int pageSize);
}
