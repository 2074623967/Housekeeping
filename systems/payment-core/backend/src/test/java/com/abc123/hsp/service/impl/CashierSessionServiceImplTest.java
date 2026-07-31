package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.CashierSessionListItemDTO;
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

        when(cashierSessionMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(cashierSessionMapper.count(query)).thenReturn(0L);
        new CashierSessionServiceImpl(cashierSessionMapper).list(query);

        assertEquals(2, query.getPageNo());
        assertEquals(100, query.getPageSize());
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("王先生", query.getCustomerName());
        assertEquals("expiresAt", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        verify(cashierSessionMapper).findAll(query);
        verify(cashierSessionMapper).count(query);
    }

    @Test
    void shouldExportNormalizedCashierSessionsAsCsv() {
        CashierSessionQueryDTO query = new CashierSessionQueryDTO();
        query.setSessionNo(" PRE-001 ");
        query.setCustomerName(" 张\"女士 ");
        query.setSortOrder(" ASC ");
        CashierSessionListItemDTO item = new CashierSessionListItemDTO();
        item.setSessionNo("PRE-001");
        item.setPrepayOrderNo("PRE-001");
        item.setCashierTitle("张\"女士的收银台");
        item.setSessionStatusType("danger");
        when(cashierSessionMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new CashierSessionServiceImpl(cashierSessionMapper).exportCsv(query);

        assertEquals("PRE-001", query.getSessionNo());
        assertEquals("张\"女士", query.getCustomerName());
        assertEquals("asc", query.getSortOrder());
        assertTrue(csv.startsWith("\uFEFF会话编号,预付单号,支付单号"));
        assertTrue(csv.contains("\"danger\""));
        assertTrue(csv.contains("\"张\"\"女士的收银台\""));
        verify(cashierSessionMapper).findAllForExport(query);
    }
}
