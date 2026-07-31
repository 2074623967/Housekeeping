package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.BillListItemDTO;
import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.mapper.BillMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 账单分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class BillServiceImplTest {

    @Mock
    private BillMapper billMapper;

    @Test
    void shouldNormalizeAndForwardBillPagingQuery() {
        BillQueryDTO query = new BillQueryDTO();
        query.setBillNo(" BILL-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setCustomerName(" 张女士 ");
        query.setBillStatus("待支付");
        query.setSortField(" dueAt ");
        query.setSortOrder(" ASC ");
        query.setPageNo(0);
        query.setPageSize(500);

        when(billMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(billMapper.count(query)).thenReturn(0L);
        new BillServiceImpl(billMapper).list(query);

        assertEquals(1, query.getPageNo());
        assertEquals(100, query.getPageSize());
        assertEquals("张女士", query.getCustomerName());
        assertEquals("dueAt", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        verify(billMapper).findAll(query);
        verify(billMapper).count(query);
    }

    @Test
    void shouldExportNormalizedBillsAsCsv() {
        BillQueryDTO query = new BillQueryDTO();
        query.setBillNo(" BILL-001 ");
        query.setCustomerName(" 张\"女士 ");
        query.setSortOrder(" ASC ");
        BillListItemDTO item = new BillListItemDTO();
        item.setBillNo("BILL-001");
        item.setOrderNo("ORD-001");
        item.setCustomerName("张\"女士");
        item.setBillAmount("¥100.00");
        item.setBillStatus("待支付");
        item.setBillStatusType("warning");
        when(billMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new BillServiceImpl(billMapper).exportCsv(query);

        assertEquals("BILL-001", query.getBillNo());
        assertEquals("张\"女士", query.getCustomerName());
        assertEquals("asc", query.getSortOrder());
        assertTrue(csv.startsWith("\uFEFF账单号,订单号,客户名称,应收金额,已付金额,待付金额,账单状态,状态类型"));
        assertTrue(csv.contains("\"warning\""));
        assertTrue(csv.contains("\"张\"\"女士\""));
        verify(billMapper).findAllForExport(query);
    }
}
