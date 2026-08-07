package com.abc123.deposit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.deposit.dto.DepositAccountQueryDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
import com.abc123.deposit.service.DepositService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 保证金控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class DepositControllerTest {

    @Mock
    private DepositService service;

    @Test
    void shouldExportAccounts() {
        DepositController controller = new DepositController(service);
        when(service.exportAccounts(org.mockito.ArgumentMatchers.any(DepositAccountQueryDTO.class)))
                .thenReturn("accounts".getBytes(StandardCharsets.UTF_8));

        byte[] body = controller.exportAccounts("WORKER", "OPEN", "DEP").getBody();

        ArgumentCaptor<DepositAccountQueryDTO> captor = ArgumentCaptor.forClass(DepositAccountQueryDTO.class);
        verify(service).exportAccounts(captor.capture());
        assertEquals("WORKER", captor.getValue().getOwnerType());
        assertEquals("OPEN", captor.getValue().getStatus());
        assertEquals("DEP", captor.getValue().getKeyword());
        assertEquals("accounts", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void shouldExportFlows() {
        DepositController controller = new DepositController(service);
        when(service.exportFlows(org.mockito.ArgumentMatchers.eq("DEP10001"),
                org.mockito.ArgumentMatchers.any(DepositFlowQueryDTO.class)))
                .thenReturn("flows".getBytes(StandardCharsets.UTF_8));

        byte[] body = controller.exportFlows("DEP10001", "FREEZE", "FREEZE-1001").getBody();

        ArgumentCaptor<DepositFlowQueryDTO> captor = ArgumentCaptor.forClass(DepositFlowQueryDTO.class);
        verify(service).exportFlows(org.mockito.ArgumentMatchers.eq("DEP10001"), captor.capture());
        assertEquals("FREEZE", captor.getValue().getFlowType());
        assertEquals("FREEZE-1001", captor.getValue().getReferenceNo());
        assertEquals("flows", new String(body, StandardCharsets.UTF_8));
    }
}
