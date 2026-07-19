package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.CashierSessionQueryDTO;
import com.abc123.hsp.mapper.CashierSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 收银台会话分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class CashierSessionServiceImplTest {

    @Mock
    private CashierSessionMapper cashierSessionMapper;

    @Test
    void shouldNormalizeAndForwardCashierSessionPagingQuery() {
        CashierSessionQueryDTO query = new CashierSessionQueryDTO();
        query.setSessionNo(" PRE-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setTerminal("H5");
        query.setSessionStatus("待支付");
        query.setPageNo(2);
        query.setPageSize(40);

        new CashierSessionServiceImpl(cashierSessionMapper).list(query);

        verify(cashierSessionMapper).findAll(query);
        verify(cashierSessionMapper).count(query);
    }
}
