package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.BillListItemDTO;
import com.abc123.hsp.dto.BillOverviewDTO;
import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
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
    void shouldRejectOversizedBillQueryBeforeMapperAccess() {
        BillQueryDTO billNoQuery = new BillQueryDTO();
        billNoQuery.setBillNo(repeat("B", 65));
        BillQueryDTO orderNoQuery = new BillQueryDTO();
        orderNoQuery.setOrderNo(repeat("O", 65));
        BillQueryDTO customerNameQuery = new BillQueryDTO();
        customerNameQuery.setCustomerName(repeat("张", 129));
        BillQueryDTO billStatusQuery = new BillQueryDTO();
        billStatusQuery.setBillStatus(repeat("S", 33));
        BillServiceImpl service = new BillServiceImpl(billMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(billNoQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(orderNoQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(customerNameQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(billStatusQuery));
        verifyNoInteractions(billMapper);
    }

    @Test
    void shouldRejectControlCharactersInBillQueryBeforeMapperAccess() {
        BillQueryDTO billNoQuery = new BillQueryDTO();
        billNoQuery.setBillNo("BILL-001\n");
        BillQueryDTO orderNoQuery = new BillQueryDTO();
        orderNoQuery.setOrderNo("ORD-001\n");
        BillQueryDTO customerNameQuery = new BillQueryDTO();
        customerNameQuery.setCustomerName("张女士\n");
        BillQueryDTO billStatusQuery = new BillQueryDTO();
        billStatusQuery.setBillStatus("待支付\n");
        BillServiceImpl service = new BillServiceImpl(billMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(billNoQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(orderNoQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(customerNameQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(billStatusQuery));
        verifyNoInteractions(billMapper);
    }

    @Test
    void shouldMaskBillListWithoutChangingMapperDto() {
        BillQueryDTO query = new BillQueryDTO();
        BillListItemDTO item = new BillListItemDTO();
        item.setCustomerName("张女士");
        when(billMapper.findAll(query)).thenReturn(Collections.singletonList(item));
        when(billMapper.count(query)).thenReturn(1L);

        PageResultDTO<BillListItemDTO> result = new BillServiceImpl(billMapper).list(query);

        assertEquals("张*", result.getItems().get(0).getCustomerName());
        assertEquals("张女士", item.getCustomerName());
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
        assertTrue(csv.contains("\"张*\""));
        assertFalse(csv.contains("张\"女士"));
        assertEquals("张\"女士", item.getCustomerName());
        verify(billMapper).findAllForExport(query);
    }

    @Test
    void shouldNormalizeAndReturnBillOverview() {
        BillQueryDTO query = new BillQueryDTO();
        query.setBillNo(" BILL-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setCustomerName(" 张女士 ");
        query.setBillStatus(" 待支付 ");
        BillOverviewDTO overview = new BillOverviewDTO();
        overview.setTotalBillCount(9L);
        overview.setOverdueBillCount(2L);
        when(billMapper.findOverview(query)).thenReturn(overview);

        BillOverviewDTO result = new BillServiceImpl(billMapper).overview(query);

        assertEquals("BILL-001", query.getBillNo());
        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("张女士", query.getCustomerName());
        assertEquals("待支付", query.getBillStatus());
        assertEquals(9L, result.getTotalBillCount());
        assertEquals(2L, result.getOverdueBillCount());
        assertEquals("¥0.00", result.getTotalUnpaidAmount());
        verify(billMapper).findOverview(query);
    }

    private String repeat(String value, int times) {
        return String.join("", Collections.nCopies(times, value));
    }
}
