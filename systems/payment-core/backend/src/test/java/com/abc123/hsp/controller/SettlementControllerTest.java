package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.service.SettlementService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 服务者结算单控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    @Mock
    private SettlementService settlementService;

    @Test
    void shouldExportWorkerSettlements() {
        SettlementController controller = new SettlementController(settlementService);
        when(settlementService.exportCsv(any(WorkerSettlementQueryDTO.class))).thenReturn("worker-csv");

        ResponseEntity<byte[]> response = controller.exportWorkerList(
                "SETTLE-001",
                "李师傅",
                "待审核",
                "待出款"
        );

        verify(settlementService).exportCsv(any(WorkerSettlementQueryDTO.class));
        assertEquals("attachment; filename=worker-settlements.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals("worker-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListWorkerSettlements() {
        SettlementController controller = new SettlementController(settlementService);

        controller.workerList(null, null, "全部", "全部", 1, 20);

        verify(settlementService).workerList(any(WorkerSettlementQueryDTO.class));
    }
}
