package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.BillOverviewDTO;
import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.service.BillService;
import java.nio.charset.StandardCharsets;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 账单中心控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class BillControllerTest {

    @Mock
    private BillService billService;

    @Test
    void shouldExportBills() {
        BillController controller = new BillController(billService);
        when(billService.exportCsv(any(BillQueryDTO.class))).thenReturn("bill-csv");

        ResponseEntity<byte[]> response = controller.export(
                "BILL-001", "ORD-001", "张女士", "待支付", "dueAt", "asc");

        ArgumentCaptor<BillQueryDTO> queryCaptor = ArgumentCaptor.forClass(BillQueryDTO.class);
        verify(billService).exportCsv(queryCaptor.capture());
        BillQueryDTO query = queryCaptor.getValue();

        assertNotNull(query);
        assertEquals("BILL-001", query.getBillNo());
        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("张女士", query.getCustomerName());
        assertEquals("待支付", query.getBillStatus());
        assertEquals("dueAt", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        assertEquals("attachment; filename=bills.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(new MediaType("text", "csv", StandardCharsets.UTF_8), response.getHeaders().getContentType());
        assertArrayEquals("bill-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListBills() {
        BillController controller = new BillController(billService);

        controller.list(null, null, null, "全部", "createdAt", "desc", 1, 20);

        verify(billService).list(any(BillQueryDTO.class));
    }

    @Test
    void shouldReturnBillOverview() {
        BillController controller = new BillController(billService);
        BillOverviewDTO overviewDTO = new BillOverviewDTO();
        overviewDTO.setTotalBillCount(10L);
        when(billService.overview(any(BillQueryDTO.class))).thenReturn(overviewDTO);

        controller.overview("BILL-001", "ORD-001", "张女士", "待支付", "dueAt", "asc");

        verify(billService).overview(any(BillQueryDTO.class));
    }
}
