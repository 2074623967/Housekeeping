package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementOverviewDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.mapper.SettlementMapper;
import com.abc123.hsp.service.SettlementService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SettlementServiceImpl implements SettlementService {

    private static final int SETTLEMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int WORKER_KEYWORD_MAX_LENGTH = 128;
    private static final int SETTLEMENT_STATUS_MAX_LENGTH = 32;
    private static final int PAYOUT_STATUS_MAX_LENGTH = 32;

    private final SettlementMapper settlementMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public SettlementServiceImpl(SettlementMapper settlementMapper,
                                 PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.settlementMapper = settlementMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    SettlementServiceImpl(SettlementMapper settlementMapper) {
        this(settlementMapper, new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PageResultDTO<WorkerSettlementListItemDTO> workerList(WorkerSettlementQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(settlementMapper.findWorkerSettlements(query)),
                settlementMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public WorkerSettlementOverviewDTO workerOverview(WorkerSettlementQueryDTO query) {
        normalizeQuery(query);
        WorkerSettlementOverviewDTO overview = settlementMapper.findWorkerOverview(query);
        if (overview == null) {
            overview = new WorkerSettlementOverviewDTO();
        }
        if (overview.getTotalSettlementCount() == null) {
            overview.setTotalSettlementCount(0L);
        }
        if (overview.getPendingAuditCount() == null) {
            overview.setPendingAuditCount(0L);
        }
        if (overview.getPayoutPendingCount() == null) {
            overview.setPayoutPendingCount(0L);
        }
        if (overview.getPayingCount() == null) {
            overview.setPayingCount(0L);
        }
        if (overview.getPayoutSuccessCount() == null) {
            overview.setPayoutSuccessCount(0L);
        }
        if (overview.getTotalNetSettleAmount() == null) {
            overview.setTotalNetSettleAmount("¥0.00");
        }
        if (overview.getTotalDeductAmount() == null) {
            overview.setTotalDeductAmount("¥0.00");
        }
        if (overview.getTotalDepositImpactAmount() == null) {
            overview.setTotalDepositImpactAmount("¥0.00");
        }
        if (overview.getNegativeNetSettleCount() == null) {
            overview.setNegativeNetSettleCount(0);
        }
        return overview;
    }

    @Override
    public String exportCsv(WorkerSettlementQueryDTO query) {
        normalizeQuery(query);
        List<WorkerSettlementListItemDTO> items = maskItems(
                CsvExportRowLimiter.requireWithinLimit(
                        settlementMapper.findWorkerSettlementsForExport(query)));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("结算单号,服务者,账期,应结金额,已扣减金额,实结金额,保证金影响,结算状态,出款状态\n");
        for (WorkerSettlementListItemDTO item : items) {
            builder.append(csvCell(item.getSettlementOrderId())).append(',')
                    .append(csvCell(item.getWorkerName())).append(',')
                    .append(csvCell(item.getPeriod())).append(',')
                    .append(csvCell(item.getAmountShouldSettle())).append(',')
                    .append(csvCell(item.getDeductAmount())).append(',')
                    .append(csvCell(item.getAmountNetSettle())).append(',')
                    .append(csvCell(item.getDepositImpactAmount())).append(',')
                    .append(csvCell(item.getStatus())).append(',')
                    .append(csvCell(item.getPayoutStatus())).append('\n');
        }
        return builder.toString();
    }

    private List<WorkerSettlementListItemDTO> maskItems(List<WorkerSettlementListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private WorkerSettlementListItemDTO maskedCopy(WorkerSettlementListItemDTO item) {
        if (item == null) {
            return null;
        }
        WorkerSettlementListItemDTO outwardItem = new WorkerSettlementListItemDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setWorkerName(sensitiveDataMasker.maskName(item.getWorkerName()));
        return outwardItem;
    }

    private void normalizeQuery(WorkerSettlementQueryDTO query) {
        query.setSettlementOrderId(normalizeOptionalQueryField(
                query.getSettlementOrderId(), SETTLEMENT_ORDER_ID_MAX_LENGTH, "结算单号", null));
        query.setWorkerKeyword(normalizeOptionalQueryField(
                query.getWorkerKeyword(), WORKER_KEYWORD_MAX_LENGTH, "服务者关键字", null));
        query.setSettlementStatus(normalizeOptionalQueryField(
                query.getSettlementStatus(), SETTLEMENT_STATUS_MAX_LENGTH, "结算状态", "全部"));
        query.setPayoutStatus(normalizeOptionalQueryField(
                query.getPayoutStatus(), PAYOUT_STATUS_MAX_LENGTH, "出款状态", "全部"));
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    private String normalizeOptionalQueryField(
            String value, int maxLength, String fieldName, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + "不能包含控制字符");
            }
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
