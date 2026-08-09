package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.BillListItemDTO;
import com.abc123.hsp.dto.BillOverviewDTO;
import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.BillMapper;
import com.abc123.hsp.service.BillService;
import org.springframework.stereotype.Service;

/**
 * 账单中心业务实现，仅承接交易账单查询，不负责账务分录处理。
 */
@Service
public class BillServiceImpl implements BillService {

    private final BillMapper billMapper;

    public BillServiceImpl(BillMapper billMapper) {
        this.billMapper = billMapper;
    }

    @Override
    public PageResultDTO<BillListItemDTO> list(BillQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                billMapper.findAll(query),
                billMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public BillOverviewDTO overview(BillQueryDTO query) {
        normalizeQuery(query);
        BillOverviewDTO overview = billMapper.findOverview(query);
        if (overview == null) {
            overview = new BillOverviewDTO();
        }
        if (overview.getTotalBillCount() == null) {
            overview.setTotalBillCount(0L);
        }
        if (overview.getPaidBillCount() == null) {
            overview.setPaidBillCount(0L);
        }
        if (overview.getUnpaidBillCount() == null) {
            overview.setUnpaidBillCount(0L);
        }
        if (overview.getPartialPaidBillCount() == null) {
            overview.setPartialPaidBillCount(0L);
        }
        if (overview.getOverdueBillCount() == null) {
            overview.setOverdueBillCount(0L);
        }
        if (overview.getTotalBillAmount() == null) {
            overview.setTotalBillAmount("¥0.00");
        }
        if (overview.getTotalPaidAmount() == null) {
            overview.setTotalPaidAmount("¥0.00");
        }
        if (overview.getTotalUnpaidAmount() == null) {
            overview.setTotalUnpaidAmount("¥0.00");
        }
        return overview;
    }

    @Override
    public String exportCsv(BillQueryDTO query) {
        normalizeQuery(query);
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("账单号,订单号,客户名称,应收金额,已付金额,待付金额,账单状态,状态类型,到期时间,创建时间\n");
        for (BillListItemDTO item : billMapper.findAllForExport(query)) {
            builder.append(csvCell(item.getBillNo())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getCustomerName())).append(',')
                    .append(csvCell(item.getBillAmount())).append(',')
                    .append(csvCell(item.getPaidAmount())).append(',')
                    .append(csvCell(item.getUnpaidAmount())).append(',')
                    .append(csvCell(item.getBillStatus())).append(',')
                    .append(csvCell(item.getBillStatusType())).append(',')
                    .append(csvCell(item.getDueAt())).append(',')
                    .append(csvCell(item.getCreatedAt())).append('\n');
        }
        return builder.toString();
    }

    private void normalizeQuery(BillQueryDTO query) {
        query.setBillNo(query.getBillNo() == null ? null : query.getBillNo().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setCustomerName(query.getCustomerName() == null ? null : query.getCustomerName().trim());
        query.setBillStatus(query.getBillStatus() == null ? "全部" : query.getBillStatus().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    private String csvCell(String value) {
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
