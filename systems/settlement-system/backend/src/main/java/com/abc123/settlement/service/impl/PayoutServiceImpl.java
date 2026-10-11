package com.abc123.settlement.service.impl;

import com.abc123.settlement.common.BusinessException;
import com.abc123.settlement.common.ErrorCode;
import com.abc123.settlement.dto.CreatePayoutBatchRequestDTO;
import com.abc123.settlement.dto.ExecutePayoutBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.PayoutRecordDTO;
import com.abc123.settlement.dto.RetryPayoutBatchRequestDTO;
import java.nio.charset.StandardCharsets;
import com.abc123.settlement.service.PayoutService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 出款服务实现。
 */
@Service
public class PayoutServiceImpl implements PayoutService {

    private final SettlementMemoryStore settlementMemoryStore;
    private final SettlementMapper settlementMapper;

    public PayoutServiceImpl(SettlementMemoryStore settlementMemoryStore, SettlementMapper settlementMapper) {
        this.settlementMemoryStore = settlementMemoryStore;
        this.settlementMapper = settlementMapper;
    }

    @Override
    public PageResultDTO<PayoutBatchDTO> list(String batchNo, String payoutStatus, int pageNo, int pageSize) {
        List<PayoutBatchDTO> items = settlementMemoryStore.payoutBatches().stream()
                .filter(item -> batchNo == null || batchNo.isEmpty() || batchNo.equals(item.getBatchNo()))
                .filter(item -> payoutStatus == null || payoutStatus.isEmpty() || payoutStatus.equals(item.getPayoutStatus()))
                .map(settlementMapper::toPayoutBatchDTO)
                .collect(Collectors.toList());
        return pageBatches(items, pageNo, pageSize);
    }

    @Override
    public byte[] exportBatches(String batchNo, String payoutStatus) {
        List<PayoutBatchDTO> items = list(batchNo, payoutStatus, 1, Integer.MAX_VALUE).getItems();
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("出款批次号,结算批次号,出款渠道,出款状态,状态样式,出款笔数,成功笔数,失败笔数,总金额,创建人,创建时间,完成时间\n");
        for (PayoutBatchDTO item : items) {
            builder.append(csv(item.getPayoutBatchNo())).append(',')
                    .append(csv(item.getBatchNo())).append(',')
                    .append(csv(item.getPayoutChannel())).append(',')
                    .append(csv(item.getPayoutStatus())).append(',')
                    .append(csv(item.getPayoutStatusType())).append(',')
                    .append(item.getPayoutCount()).append(',')
                    .append(item.getSuccessCount()).append(',')
                    .append(item.getFailedCount()).append(',')
                    .append(csv(item.getTotalAmount())).append(',')
                    .append(csv(item.getCreatedBy())).append(',')
                    .append(csv(item.getCreatedAt())).append(',')
                    .append(csv(item.getFinishedAt())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public PayoutBatchDTO create(CreatePayoutBatchRequestDTO request) {
        return settlementMapper.toPayoutBatchDTO(
                settlementMemoryStore.createOrRefreshDraftPayoutBatch(
                        request.getBatchNo(),
                        request.getPayoutChannel(),
                        request.getCreatedBy()));
    }

    @Override
    public PayoutBatchDTO execute(String payoutBatchNo, ExecutePayoutBatchRequestDTO request) {
        if (settlementMemoryStore.findPayoutBatch(payoutBatchNo) == null) {
            throw new BusinessException(ErrorCode.PAYOUT_BATCH_NOT_FOUND, "出款批次不存在");
        }
        return settlementMapper.toPayoutBatchDTO(
                settlementMemoryStore.executePendingPayoutBatch(payoutBatchNo, request));
    }

    @Override
    public PayoutBatchDTO retry(String payoutBatchNo, RetryPayoutBatchRequestDTO request) {
        if (settlementMemoryStore.findPayoutBatch(payoutBatchNo) == null) {
            throw new BusinessException(ErrorCode.PAYOUT_BATCH_NOT_FOUND, "出款批次不存在");
        }
        return settlementMapper.toPayoutBatchDTO(settlementMemoryStore.retryPayoutBatch(payoutBatchNo, request.getOperatorName(), request.getReason()));
    }

    @Override
    public PageResultDTO<PayoutRecordDTO> records(String payoutBatchNo, String payoutStatus, int pageNo, int pageSize) {
        if (settlementMemoryStore.findPayoutBatch(payoutBatchNo) == null) {
            throw new BusinessException(ErrorCode.PAYOUT_BATCH_NOT_FOUND, "出款批次不存在");
        }
        List<PayoutRecordDTO> items = settlementMemoryStore.payoutRecordsByBatchNo(payoutBatchNo).stream()
                .filter(item -> payoutStatus == null || payoutStatus.isEmpty() || payoutStatus.equals(item.getPayoutStatus()))
                .map(settlementMapper::toPayoutRecordDTO)
                .collect(Collectors.toList());
        return pageRecords(items, pageNo, pageSize);
    }

    @Override
    public byte[] exportRecords(String payoutBatchNo, String payoutStatus) {
        List<PayoutRecordDTO> items = records(payoutBatchNo, payoutStatus, 1, Integer.MAX_VALUE).getItems();
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("出款单号,出款批次号,结算单号,对象编号,对象名称,出款金额,出款状态,状态样式,重试次数,创建时间\n");
        for (PayoutRecordDTO item : items) {
            builder.append(csv(item.getPayoutNo())).append(',')
                    .append(csv(item.getPayoutBatchNo())).append(',')
                    .append(csv(item.getSettlementNo())).append(',')
                    .append(csv(item.getTargetNo())).append(',')
                    .append(csv(item.getTargetName())).append(',')
                    .append(csv(item.getPayoutAmount())).append(',')
                    .append(csv(item.getPayoutStatus())).append(',')
                    .append(csv(item.getPayoutStatusType())).append(',')
                    .append(csv(item.getRetryCount())).append(',')
                    .append(csv(item.getCreatedAt())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private PageResultDTO<PayoutBatchDTO> pageBatches(List<PayoutBatchDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    private PageResultDTO<PayoutRecordDTO> pageRecords(List<PayoutRecordDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
