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
import com.abc123.deposit.service.DepositService;
import java.math.BigDecimal;
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
}
