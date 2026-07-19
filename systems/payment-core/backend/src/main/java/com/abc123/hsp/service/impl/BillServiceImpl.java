package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.BillListItemDTO;
import com.abc123.hsp.mapper.BillMapper;
import com.abc123.hsp.service.BillService;
import java.util.List;
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
    public List<BillListItemDTO> list() {
        return billMapper.findAll();
    }
}
