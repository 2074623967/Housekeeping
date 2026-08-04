package com.abc123.reconciliation.service;

import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.DifferenceResolveRequestDTO;
import com.abc123.reconciliation.dto.InternalRecordRequestDTO;
import com.abc123.reconciliation.dto.PageResultDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchCreateRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;

/**
 * 对账业务服务。
 */
public interface ReconciliationService {

    ReconciliationBatchListItemDTO createBatch(ReconciliationBatchCreateRequestDTO request);

    ReconciliationBatchListItemDTO addChannelRecord(String batchNo, ChannelRecordRequestDTO request);

    ReconciliationBatchListItemDTO addInternalRecord(String batchNo, InternalRecordRequestDTO request);

    ReconciliationBatchListItemDTO run(String batchNo);

    java.util.List<ReconciliationBatchListItemDTO> batches();

    PageResultDTO<ReconciliationDifferenceDTO> differences(DifferenceQueryDTO query);

    void resolve(DifferenceResolveRequestDTO request);

    ReconciliationOverviewDTO overview();
}

