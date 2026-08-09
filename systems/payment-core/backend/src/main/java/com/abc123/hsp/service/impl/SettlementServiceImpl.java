package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementOverviewDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.mapper.SettlementMapper;
import com.abc123.hsp.service.SettlementService;
import org.springframework.stereotype.Service;

@Service
public class SettlementServiceImpl implements SettlementService {

    private final SettlementMapper settlementMapper;

    public SettlementServiceImpl(SettlementMapper settlementMapper) {
        this.settlementMapper = settlementMapper;
    }

    @Override
    public PageResultDTO<WorkerSettlementListItemDTO> workerList(WorkerSettlementQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                settlementMapper.findWorkerSettlements(query),
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
        java.util.List<WorkerSettlementListItemDTO> items = settlementMapper.findWorkerSettlementsForExport(query);
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

    private void normalizeQuery(WorkerSettlementQueryDTO query) {
        query.setSettlementOrderId(query.getSettlementOrderId() == null ? null : query.getSettlementOrderId().trim());
        query.setWorkerKeyword(query.getWorkerKeyword() == null ? null : query.getWorkerKeyword().trim());
        query.setSettlementStatus(query.getSettlementStatus() == null ? "全部" : query.getSettlementStatus().trim());
        query.setPayoutStatus(query.getPayoutStatus() == null ? "全部" : query.getPayoutStatus().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    private String csvCell(String value) {
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
