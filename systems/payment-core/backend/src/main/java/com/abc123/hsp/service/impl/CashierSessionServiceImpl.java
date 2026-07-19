package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.CashierSessionListItemDTO;
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
    public List<CashierSessionListItemDTO> list() {
        return cashierSessionMapper.findAll();
    }
}
