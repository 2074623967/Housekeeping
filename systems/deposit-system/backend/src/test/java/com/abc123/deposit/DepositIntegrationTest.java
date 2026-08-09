package com.abc123.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.deposit.common.BusinessException;
import com.abc123.deposit.dto.DebtOffsetRequestDTO;
import com.abc123.deposit.dto.DepositAccountCreateRequestDTO;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositActionRequestDTO;
import com.abc123.deposit.service.DepositService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证保证金收取、冻结、解冻和扣罚链路。
 */
@SpringBootTest
class DepositIntegrationTest {

    @Autowired
    private DepositService service;

    @Test
    void shouldCompleteDepositLifecycle() {
        DepositAccountCreateRequestDTO create = new DepositAccountCreateRequestDTO();
        create.setOwnerId("WORKER-" + UUID.randomUUID());
        create.setOwnerType("WORKER");
        create.setRequiredAmount(new BigDecimal("500.00"));
        DepositAccountDTO account = service.openAccount(create);

        DepositActionRequestDTO action = new DepositActionRequestDTO();
        action.setAccountNo(account.getAccountNo());
        action.setAmount(new BigDecimal("500.00"));
        assertEquals(new BigDecimal("500.00"), service.collect(action).getBalance());

        action.setAmount(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("100.00"), service.freeze(action).getFrozenAmount());
        assertEquals(new BigDecimal("400.00"), service.deduct(action).getBalance());
        assertEquals(new BigDecimal("0.00"), service.unfreeze(action).getFrozenAmount());
        assertEquals(4, service.flows(account.getAccountNo()).size());
    }

    @Test
    void shouldRefundAndOffsetDebtWithAuditableFlowTypes() {
        DepositAccountCreateRequestDTO create = new DepositAccountCreateRequestDTO();
        create.setOwnerId("WORKER-" + UUID.randomUUID());
        create.setOwnerType("WORKER");
        DepositAccountDTO account = service.openAccount(create);

        DepositActionRequestDTO action = new DepositActionRequestDTO();
        action.setAccountNo(account.getAccountNo());
        action.setAmount(new BigDecimal("300.00"));
        action.setReferenceNo("COLLECT-REF-1");
        assertEquals(new BigDecimal("300.00"), service.collect(action).getBalance());

        action.setAmount(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("200.00"), service.offsetDebt(offset(account.getAccountNo(), "DEBT-1")).getBalance());
        action.setReferenceNo("REFUND-REF-1");
        assertEquals(new BigDecimal("300.00"), service.refund(action).getBalance());
        assertEquals("REFUND", service.flows(account.getAccountNo()).get(0).getFlowType());
        assertEquals("OFFSET_DEBT", service.flows(account.getAccountNo()).get(1).getFlowType());
    }

    @Test
    void shouldRejectOperationWhenAvailableBalanceIsInsufficient() {
        DepositAccountCreateRequestDTO create = new DepositAccountCreateRequestDTO();
        create.setOwnerId("WORKER-" + UUID.randomUUID());
        create.setOwnerType("WORKER");
        DepositAccountDTO account = service.openAccount(create);

        DepositActionRequestDTO action = new DepositActionRequestDTO();
        action.setAccountNo(account.getAccountNo());
        action.setAmount(new BigDecimal("10.00"));
        assertThrows(BusinessException.class, () -> service.deduct(action));
    }

    @Test
    void shouldRejectDuplicateAccountForSameOwner() {
        String ownerId = "WORKER-" + UUID.randomUUID();
        DepositAccountCreateRequestDTO create = new DepositAccountCreateRequestDTO();
        create.setOwnerId(ownerId);
        create.setOwnerType("WORKER");
        service.openAccount(create);

        DepositAccountCreateRequestDTO duplicated = new DepositAccountCreateRequestDTO();
        duplicated.setOwnerId(ownerId);
        duplicated.setOwnerType("WORKER");
        assertThrows(BusinessException.class, () -> service.openAccount(duplicated));
    }

    @Test
    void shouldRejectDuplicateReferenceForSameFlowType() {
        DepositAccountCreateRequestDTO create = new DepositAccountCreateRequestDTO();
        create.setOwnerId("WORKER-" + UUID.randomUUID());
        create.setOwnerType("WORKER");
        DepositAccountDTO account = service.openAccount(create);

        DepositActionRequestDTO action = new DepositActionRequestDTO();
        action.setAccountNo(account.getAccountNo());
        action.setAmount(new BigDecimal("150.00"));
        action.setReferenceNo("COLLECT-IDEMPOTENT-1");
        assertEquals(new BigDecimal("150.00"), service.collect(action).getBalance());

        DepositActionRequestDTO duplicated = new DepositActionRequestDTO();
        duplicated.setAccountNo(account.getAccountNo());
        duplicated.setAmount(new BigDecimal("150.00"));
        duplicated.setReferenceNo("COLLECT-IDEMPOTENT-1");
        assertThrows(BusinessException.class, () -> service.collect(duplicated));
        assertEquals(1, service.flows(account.getAccountNo()).size());
        assertEquals(new BigDecimal("150.00"), service.accounts().stream()
                .filter(item -> account.getAccountNo().equals(item.getAccountNo()))
                .findFirst()
                .orElseThrow(AssertionError::new)
                .getBalance());
    }

    private DebtOffsetRequestDTO offset(String accountNo, String debtNo) {
        DebtOffsetRequestDTO request = new DebtOffsetRequestDTO();
        request.setAccountNo(accountNo);
        request.setDebtNo(debtNo);
        request.setDebtAmount(new BigDecimal("100.00"));
        return request;
    }
}
