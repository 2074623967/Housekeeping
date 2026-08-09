package com.abc123.reconciliation.service;

import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.DifferenceResolveRequestDTO;
import com.abc123.reconciliation.dto.InternalRecordRequestDTO;
import com.abc123.reconciliation.dto.PageResultDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchCreateRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchDetailDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.dto.CrossSystemChainSnapshotDTO;
import com.abc123.reconciliation.dto.CrossSystemFactRequestDTO;

/**
 * 对账业务服务。
 */
public interface ReconciliationService {

    ReconciliationBatchListItemDTO createBatch(ReconciliationBatchCreateRequestDTO request);

    ReconciliationBatchListItemDTO addChannelRecord(String batchNo, ChannelRecordRequestDTO request);

    ReconciliationBatchListItemDTO addInternalRecord(String batchNo, InternalRecordRequestDTO request);

    ReconciliationBatchListItemDTO run(String batchNo);

    ReconciliationBatchListItemDTO checkBatchReadiness(String batchNo);

    ReconciliationBatchDetailDTO detail(String batchNo);

    byte[] exportBatchDetail(String batchNo);

    java.util.List<ReconciliationBatchListItemDTO> batches();

    PageResultDTO<ReconciliationDifferenceDTO> differences(DifferenceQueryDTO query);

    void resolve(DifferenceResolveRequestDTO request);

    ReconciliationOverviewDTO overview();

    CrossSystemChainSnapshotDTO recordCrossSystemFact(CrossSystemFactRequestDTO request);

    CrossSystemChainSnapshotDTO crossSystemChain(String businessChainId);
}
