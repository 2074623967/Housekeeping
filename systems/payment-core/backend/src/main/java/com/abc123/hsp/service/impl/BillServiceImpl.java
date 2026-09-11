package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.BillListItemDTO;
import com.abc123.hsp.dto.BillOverviewDTO;
import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.BillMapper;
import com.abc123.hsp.service.BillService;
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

/**
 * 账单中心业务实现，仅承接交易账单查询，不负责账务分录处理。
 */
@Service
public class BillServiceImpl implements BillService {

    private static final int BILL_NO_MAX_LENGTH = 64;
    private static final int ORDER_NO_MAX_LENGTH = 64;
    private static final int CUSTOMER_NAME_MAX_LENGTH = 128;
    private static final int BILL_STATUS_MAX_LENGTH = 32;

    private final BillMapper billMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public BillServiceImpl(BillMapper billMapper, PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.billMapper = billMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    BillServiceImpl(BillMapper billMapper) {
        this(billMapper, new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PageResultDTO<BillListItemDTO> list(BillQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(billMapper.findAll(query)),
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
        for (BillListItemDTO item : maskItems(
                CsvExportRowLimiter.requireWithinLimit(billMapper.findAllForExport(query)))) {
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

    private List<BillListItemDTO> maskItems(List<BillListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private BillListItemDTO maskedCopy(BillListItemDTO item) {
        if (item == null) {
            return null;
        }
        BillListItemDTO outwardItem = new BillListItemDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setCustomerName(sensitiveDataMasker.maskName(item.getCustomerName()));
        return outwardItem;
    }

    private void normalizeQuery(BillQueryDTO query) {
        query.setBillNo(normalizeOptionalQueryField(
                query.getBillNo(), BILL_NO_MAX_LENGTH, "账单号", null));
        query.setOrderNo(normalizeOptionalQueryField(
                query.getOrderNo(), ORDER_NO_MAX_LENGTH, "订单号", null));
        query.setCustomerName(normalizeOptionalQueryField(
                query.getCustomerName(), CUSTOMER_NAME_MAX_LENGTH, "客户名称", null));
        query.setBillStatus(normalizeOptionalQueryField(
                query.getBillStatus(), BILL_STATUS_MAX_LENGTH, "账单状态", "全部"));
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
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
