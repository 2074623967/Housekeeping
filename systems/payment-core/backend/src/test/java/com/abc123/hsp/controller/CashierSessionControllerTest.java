package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.CashierSessionOverviewDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
import com.abc123.hsp.service.CashierSessionService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 收银台会话控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class CashierSessionControllerTest {

    @Mock
    private CashierSessionService cashierSessionService;

    @Test
    void shouldExportCashierSessions() {
        CashierSessionController controller = new CashierSessionController(cashierSessionService);
        when(cashierSessionService.exportCsv(any(CashierSessionQueryDTO.class))).thenReturn("session-csv");

        ResponseEntity<byte[]> response = controller.export(
                "PRE-001", "PAY-001", "ORD-001", "张女士", "H5", "待支付", "expiresAt", "asc");

        ArgumentCaptor<CashierSessionQueryDTO> queryCaptor = ArgumentCaptor.forClass(CashierSessionQueryDTO.class);
        verify(cashierSessionService).exportCsv(queryCaptor.capture());
        CashierSessionQueryDTO query = queryCaptor.getValue();

        assertNotNull(query);
        assertEquals("PRE-001", query.getSessionNo());
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("张女士", query.getCustomerName());
        assertEquals("H5", query.getTerminal());
        assertEquals("待支付", query.getSessionStatus());
        assertEquals("expiresAt", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        assertEquals("attachment; filename=cashier-sessions.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(new MediaType("text", "csv", StandardCharsets.UTF_8), response.getHeaders().getContentType());
        assertArrayEquals("session-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListCashierSessions() {
        CashierSessionController controller = new CashierSessionController(cashierSessionService);

        controller.list(null, null, null, null, "全部", "全部", "createdAt", "desc", 1, 20);

        verify(cashierSessionService).list(any(CashierSessionQueryDTO.class));
    }

    @Test
    void shouldReturnCashierSessionOverview() {
        CashierSessionController controller = new CashierSessionController(cashierSessionService);
        CashierSessionOverviewDTO overviewDTO = new CashierSessionOverviewDTO();
        overviewDTO.setTotalSessionCount(10L);
        when(cashierSessionService.overview(any(CashierSessionQueryDTO.class))).thenReturn(overviewDTO);

        controller.overview("PRE-001", "PAY-001", "ORD-001", "张女士", "H5", "待支付", "createdAt", "desc");

        verify(cashierSessionService).overview(any(CashierSessionQueryDTO.class));
    }
}
