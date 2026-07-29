package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
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
        query.setSettlementOrderId(query.getSettlementOrderId() == null ? null : query.getSettlementOrderId().trim());
        query.setWorkerKeyword(query.getWorkerKeyword() == null ? null : query.getWorkerKeyword().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                settlementMapper.findWorkerSettlements(query),
                settlementMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(WorkerSettlementQueryDTO query) {
        query.setSettlementOrderId(query.getSettlementOrderId() == null ? null : query.getSettlementOrderId().trim());
        query.setWorkerKeyword(query.getWorkerKeyword() == null ? null : query.getWorkerKeyword().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
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

    private String csvCell(String value) {
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
