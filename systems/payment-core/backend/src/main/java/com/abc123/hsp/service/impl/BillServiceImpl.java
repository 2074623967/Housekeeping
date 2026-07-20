package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.BillListItemDTO;
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
        query.setBillNo(query.getBillNo() == null ? null : query.getBillNo().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setCustomerName(query.getCustomerName() == null ? null : query.getCustomerName().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                billMapper.findAll(query),
                billMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }
}
