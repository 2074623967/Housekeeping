package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
import com.abc123.hsp.mapper.CashierSessionMapper;
import com.abc123.hsp.service.CashierSessionService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 收银台会话业务实现。
 */
@Service
public class CashierSessionServiceImpl implements CashierSessionService {

    private final CashierSessionMapper cashierSessionMapper;

    public CashierSessionServiceImpl(CashierSessionMapper cashierSessionMapper) {
        this.cashierSessionMapper = cashierSessionMapper;
    }

    @Override
    public List<CashierSessionListItemDTO> list(CashierSessionQueryDTO query) {
        query.setSessionNo(query.getSessionNo() == null ? null : query.getSessionNo().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        return cashierSessionMapper.findAll(query);
    }
}
