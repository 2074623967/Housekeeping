package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.CashierSessionQueryDTO;
import com.abc123.hsp.mapper.CashierSessionMapper;
import java.util.Collections;
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
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setCustomerName(" 王先生 ");
        query.setTerminal("H5");
        query.setSessionStatus("待支付");
        query.setSortField(" expiresAt ");
        query.setSortOrder(" ASC ");
        query.setPageNo(2);
        query.setPageSize(999);

        org.mockito.Mockito.when(cashierSessionMapper.findAll(query)).thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(cashierSessionMapper.count(query)).thenReturn(0L);
        new CashierSessionServiceImpl(cashierSessionMapper).list(query);

        org.junit.jupiter.api.Assertions.assertEquals(2, query.getPageNo());
        org.junit.jupiter.api.Assertions.assertEquals(100, query.getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals("PAY-001", query.getPaymentOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("王先生", query.getCustomerName());
        org.junit.jupiter.api.Assertions.assertEquals("expiresAt", query.getSortField());
        org.junit.jupiter.api.Assertions.assertEquals("asc", query.getSortOrder());
        verify(cashierSessionMapper).findAll(query);
        verify(cashierSessionMapper).count(query);
    }
}
