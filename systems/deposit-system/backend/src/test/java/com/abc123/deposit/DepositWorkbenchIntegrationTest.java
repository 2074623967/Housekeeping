package com.abc123.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.deposit.dto.DepositAccountCreateRequestDTO;
import com.abc123.deposit.dto.DepositAccountDetailDTO;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositAccountQueryDTO;
import com.abc123.deposit.dto.DepositActionRequestDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
import com.abc123.deposit.dto.DepositOverviewDTO;
import com.abc123.deposit.dto.DepositTotalAccountDetailDTO;
import com.abc123.deposit.dto.DepositV2OverviewDTO;
import com.abc123.deposit.service.DepositService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证保证金工作台查询和详情能力。
 */
@SpringBootTest
class DepositWorkbenchIntegrationTest {

    @Autowired
    private DepositService service;

    @Test
    void shouldSupportOverviewAccountDetailAndFlowFilters() {
        DepositAccountCreateRequestDTO create = new DepositAccountCreateRequestDTO();
        create.setOwnerId("MERCHANT-" + UUID.randomUUID());
        create.setOwnerType("MERCHANT");
        create.setRequiredAmount(new BigDecimal("1000.00"));
        DepositAccountDTO account = service.openAccount(create);

        DepositActionRequestDTO collect = new DepositActionRequestDTO();
        collect.setAccountNo(account.getAccountNo());
        collect.setAmount(new BigDecimal("600.00"));
        collect.setReferenceNo("COLLECT-WORKBENCH-1");
        service.collect(collect);

        DepositActionRequestDTO freeze = new DepositActionRequestDTO();
        freeze.setAccountNo(account.getAccountNo());
        freeze.setAmount(new BigDecimal("200.00"));
        freeze.setReferenceNo("FREEZE-WORKBENCH-1");
        service.freeze(freeze);

        DepositOverviewDTO overview = service.overview();
        assertTrue(overview.getAccountCount() > 0);
        assertTrue(overview.getTotalBalance().compareTo(new BigDecimal("600.00")) >= 0);

        DepositAccountQueryDTO query = new DepositAccountQueryDTO();
        query.setOwnerType("MERCHANT");
        query.setKeyword(account.getAccountNo());
        assertEquals(1, service.accounts(query).stream()
                .filter(item -> account.getAccountNo().equals(item.getAccountNo()))
                .count());

        DepositAccountDetailDTO detail = service.accountDetail(account.getAccountNo());
        assertEquals(account.getAccountNo(), detail.getAccountNo());
        assertEquals(Integer.valueOf(2), detail.getFlowCount());
        assertFalse(detail.getFullyPaid());
        assertEquals(new BigDecimal("400.00"), detail.getOutstandingAmount());

        DepositFlowQueryDTO flowQuery = new DepositFlowQueryDTO();
        flowQuery.setFlowType("FREEZE");
        assertEquals(1, service.flows(account.getAccountNo(), flowQuery).size());
        flowQuery.setFlowType(null);
        flowQuery.setReferenceNo("COLLECT-WORKBENCH");
        assertEquals(1, service.flows(account.getAccountNo(), flowQuery).size());
    }

    @Test
    void shouldExposeV2EvolutionPreview() {
        DepositAccountCreateRequestDTO worker = new DepositAccountCreateRequestDTO();
        worker.setOwnerId("WORKER-" + UUID.randomUUID());
        worker.setOwnerType("WORKER");
        worker.setRequiredAmount(new BigDecimal("800.00"));
        DepositAccountDTO workerAccount = service.openAccount(worker);

        DepositActionRequestDTO workerCollect = new DepositActionRequestDTO();
        workerCollect.setAccountNo(workerAccount.getAccountNo());
        workerCollect.setAmount(new BigDecimal("300.00"));
        workerCollect.setReferenceNo("V2-WORKER-COLLECT-1");
        service.collect(workerCollect);

        DepositAccountCreateRequestDTO merchant = new DepositAccountCreateRequestDTO();
        merchant.setOwnerId("MERCHANT-" + UUID.randomUUID());
        merchant.setOwnerType("MERCHANT");
        merchant.setRequiredAmount(new BigDecimal("1200.00"));
        DepositAccountDTO merchantAccount = service.openAccount(merchant);

        DepositActionRequestDTO merchantCollect = new DepositActionRequestDTO();
        merchantCollect.setAccountNo(merchantAccount.getAccountNo());
        merchantCollect.setAmount(new BigDecimal("500.00"));
        merchantCollect.setReferenceNo("V2-MERCHANT-COLLECT-1");
        service.collect(merchantCollect);

        DepositV2OverviewDTO overview = service.v2Overview();
        assertTrue(overview.getTotalAccountCount() >= 2);
        assertTrue(overview.getBindingCount() >= 2);
        assertTrue(overview.getMigrationCandidateCount() >= 2);

        String totalAccountNo = service.totalAccounts().stream()
                .filter(item -> "WORKER".equals(item.getOwnerType()))
                .findFirst()
                .orElseThrow(AssertionError::new)
                .getTotalAccountNo();
        DepositTotalAccountDetailDTO detail = service.totalAccountDetail(totalAccountNo);
        assertEquals("WORKER", detail.getSummary().getOwnerType());
        assertFalse(detail.getBindings().isEmpty());
        assertFalse(detail.getMigrations().isEmpty());
        assertTrue(detail.getMigrations().stream()
                .anyMatch(item -> workerAccount.getAccountNo().equals(item.getSourceAccountNo())));
    }

    @Test
    void shouldExportAccountAndFlowSnapshots() {
        DepositAccountCreateRequestDTO create = new DepositAccountCreateRequestDTO();
        create.setOwnerId("WORKER-" + UUID.randomUUID());
        create.setOwnerType("WORKER");
        create.setRequiredAmount(new BigDecimal("700.00"));
        DepositAccountDTO account = service.openAccount(create);

        DepositActionRequestDTO collect = new DepositActionRequestDTO();
        collect.setAccountNo(account.getAccountNo());
        collect.setAmount(new BigDecimal("300.00"));
        collect.setReferenceNo("EXPORT-COLLECT-1");
        service.collect(collect);

        DepositAccountQueryDTO accountQuery = new DepositAccountQueryDTO();
        accountQuery.setKeyword(account.getAccountNo());
        String accountsCsv = new String(service.exportAccounts(accountQuery), StandardCharsets.UTF_8);

        DepositFlowQueryDTO flowQuery = new DepositFlowQueryDTO();
        flowQuery.setReferenceNo("EXPORT-COLLECT");
        String flowsCsv = new String(service.exportFlows(account.getAccountNo(), flowQuery), StandardCharsets.UTF_8);

        assertTrue(accountsCsv.contains("账户号,主体编号,主体类型"));
        assertTrue(accountsCsv.contains(account.getAccountNo()));
        assertTrue(flowsCsv.contains("流水号,账户号,动作类型"));
        assertTrue(flowsCsv.contains(account.getAccountNo()));
        assertTrue(flowsCsv.contains("EXPORT-COLLECT-1"));
    }
}
