package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.CashierSessionDetailDTO;
import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.dto.CashierSessionOverviewDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
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
    void shouldNormalizeAndMaskCashierSessionDetailWithoutChangingMapperDto() {
        CashierSessionDetailDTO mapperDetail = new CashierSessionDetailDTO();
        mapperDetail.setSessionNo("PRE-001");
        mapperDetail.setCustomerName("王先生");
        when(cashierSessionMapper.findDetail("PRE-001")).thenReturn(mapperDetail);

        CashierSessionDetailDTO result =
                new CashierSessionServiceImpl(cashierSessionMapper).detail(" PRE-001 ");

        assertEquals("PRE-001", result.getSessionNo());
        assertEquals("王*", result.getCustomerName());
        assertEquals("王先生", mapperDetail.getCustomerName());
        verify(cashierSessionMapper).findDetail("PRE-001");
    }

    @Test
    void shouldRejectInvalidCashierSessionDetailIdentifierBeforeMapperAccess() {
        CashierSessionServiceImpl service = new CashierSessionServiceImpl(cashierSessionMapper);

        BusinessException empty = assertThrows(BusinessException.class, () -> service.detail("  "));
        BusinessException control = assertThrows(
                BusinessException.class, () -> service.detail("PRE-001\n"));
        BusinessException oversized = assertThrows(
                BusinessException.class, () -> service.detail(repeat("S", 64) + " "));

        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, empty.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, control.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, oversized.getCode());
        verifyNoInteractions(cashierSessionMapper);
    }

    @Test
    void shouldReportMissingCashierSessionDetail() {
        when(cashierSessionMapper.findDetail("PRE-MISSING")).thenReturn(null);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> new CashierSessionServiceImpl(cashierSessionMapper).detail("PRE-MISSING"));

        assertEquals(ErrorCode.CASHIER_SESSION_NOT_FOUND, missing.getCode());
        assertEquals("收银台会话不存在", missing.getMessage());
    }

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
    void shouldRejectOversizedCashierSessionQueryBeforeMapperAccess() {
        CashierSessionQueryDTO sessionQuery = new CashierSessionQueryDTO();
        sessionQuery.setSessionNo(repeat("S", 65));
        CashierSessionQueryDTO paymentOrderQuery = new CashierSessionQueryDTO();
        paymentOrderQuery.setPaymentOrderId(repeat("P", 65));
        CashierSessionQueryDTO orderQuery = new CashierSessionQueryDTO();
        orderQuery.setOrderNo(repeat("O", 65));
        CashierSessionQueryDTO customerQuery = new CashierSessionQueryDTO();
        customerQuery.setCustomerName(repeat("张", 129));
        CashierSessionQueryDTO terminalQuery = new CashierSessionQueryDTO();
        terminalQuery.setTerminal(repeat("T", 33));
        CashierSessionQueryDTO statusQuery = new CashierSessionQueryDTO();
        statusQuery.setSessionStatus(repeat("C", 33));
        CashierSessionServiceImpl service = new CashierSessionServiceImpl(cashierSessionMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(sessionQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(customerQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(terminalQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(statusQuery));
        verifyNoInteractions(cashierSessionMapper);
    }

    @Test
    void shouldRejectControlCharactersInCashierSessionQueryBeforeMapperAccess() {
        CashierSessionQueryDTO sessionQuery = new CashierSessionQueryDTO();
        sessionQuery.setSessionNo("PRE-001\n");
        CashierSessionQueryDTO paymentOrderQuery = new CashierSessionQueryDTO();
        paymentOrderQuery.setPaymentOrderId("PAY-001\n");
        CashierSessionQueryDTO orderQuery = new CashierSessionQueryDTO();
        orderQuery.setOrderNo("ORD-001\n");
        CashierSessionQueryDTO customerQuery = new CashierSessionQueryDTO();
        customerQuery.setCustomerName("张女士\n");
        CashierSessionQueryDTO terminalQuery = new CashierSessionQueryDTO();
        terminalQuery.setTerminal("H5\n");
        CashierSessionQueryDTO statusQuery = new CashierSessionQueryDTO();
        statusQuery.setSessionStatus("待支付\n");
        CashierSessionServiceImpl service = new CashierSessionServiceImpl(cashierSessionMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(sessionQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(customerQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(terminalQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(statusQuery));
        verifyNoInteractions(cashierSessionMapper);
    }

    @Test
    void shouldMaskCashierSessionListWithoutChangingMapperDto() {
        CashierSessionQueryDTO query = new CashierSessionQueryDTO();
        CashierSessionListItemDTO item = new CashierSessionListItemDTO();
        item.setCustomerName("王先生");
        when(cashierSessionMapper.findAll(query))
                .thenReturn(java.util.Arrays.asList((CashierSessionListItemDTO) null, item));
        when(cashierSessionMapper.count(query)).thenReturn(1L);

        PageResultDTO<CashierSessionListItemDTO> result =
                new CashierSessionServiceImpl(cashierSessionMapper).list(query);

        assertEquals("王*", result.getItems().get(0).getCustomerName());
        assertEquals("王先生", item.getCustomerName());
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
        item.setCustomerName("张\"女士");
        item.setCashierTitle("张\"女士的收银台");
        item.setSessionStatusType("danger");
        when(cashierSessionMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new CashierSessionServiceImpl(cashierSessionMapper).exportCsv(query);

        assertEquals("PRE-001", query.getSessionNo());
        assertEquals("张\"女士", query.getCustomerName());
        assertEquals("asc", query.getSortOrder());
        assertTrue(csv.startsWith("\uFEFF会话编号,预付单号,支付单号"));
        assertTrue(csv.contains("\"danger\""));
        assertTrue(csv.contains("\"张*\""));
        assertFalse(csv.contains("\"张\"\"女士\","));
        assertTrue(csv.contains("\"张\"\"女士的收银台\""));
        assertEquals("张\"女士", item.getCustomerName());
        verify(cashierSessionMapper).findAllForExport(query);
    }

    @Test
    void shouldNormalizeAndReturnCashierSessionOverview() {
        CashierSessionQueryDTO query = new CashierSessionQueryDTO();
        query.setSessionNo(" PRE-001 ");
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setCustomerName(" 王先生 ");
        query.setTerminal(" H5 ");
        query.setSessionStatus(" 待支付 ");
        CashierSessionOverviewDTO overview = new CashierSessionOverviewDTO();
        overview.setTotalSessionCount(12L);
        overview.setExpiringSoonCount(2);
        when(cashierSessionMapper.findOverview(query)).thenReturn(overview);

        CashierSessionOverviewDTO result = new CashierSessionServiceImpl(cashierSessionMapper).overview(query);

        assertEquals("PRE-001", query.getSessionNo());
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("王先生", query.getCustomerName());
        assertEquals("H5", query.getTerminal());
        assertEquals("待支付", query.getSessionStatus());
        assertEquals(12L, result.getTotalSessionCount());
        assertEquals(2, result.getExpiringSoonCount());
        assertEquals("¥0.00", result.getTotalAmount());
        verify(cashierSessionMapper).findOverview(query);
    }

    private String repeat(String value, int times) {
        return String.join("", Collections.nCopies(times, value));
    }
}
